package ink.doa.workbench.jobs.project

import ink.doa.workbench.core.messaging.EventMetadata
import ink.doa.workbench.core.project.events.ProjectDestroyRequestedEvent
import ink.doa.workbench.core.project.events.ProjectDestroyedEvent
import ink.doa.workbench.core.project.events.ProjectDomainEvents
import ink.doa.workbench.jobs.messaging.DomainEventHandler
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@ink.doa.workbench.jobs.JobsEnabled
@Component
class ProjectDestroyRequestedEventHandler(
  private val lookup: ProjectDestroyLookupSupport,
  private val runtime: ProjectDestroyRuntimeSupport,
) : DomainEventHandler<ProjectDestroyRequestedEvent> {
  private val logger = LoggerFactory.getLogger(javaClass)

  override suspend fun handle(payload: ProjectDestroyRequestedEvent) {
    val tenant =
      lookup.tenants.findByApiId(payload.tenantId)
        ?: run {
          logger.warn(
            "project_destroy_requested_skipped tenantId={} reason=tenant_not_found",
            payload.tenantId,
          )
          return
        }
    val project =
      lookup.projects.findByApiId(tenant.id, payload.projectId)
        ?: run {
          logger.warn(
            "project_destroy_requested_skipped projectId={} reason=project_not_found",
            payload.projectId,
          )
          return
        }

    val actor =
      lookup.users.findByApiId(payload.requestedBy)
        ?: run {
          logger.warn(
            "project_destroy_requested_skipped projectId={} reason=actor_not_found actor={}",
            payload.projectId,
            payload.requestedBy,
          )
          return
        }

    val completed =
      runtime.distributedLockService.withLock(
        name = "project-destroy:${project.id}",
        wait = Duration.ofSeconds(5),
        lease = Duration.ofMinutes(10),
      ) {
        runBlocking {
          runtime.projectDestructionService.execute(
            tenantId = tenant.id,
            projectId = project.id,
            deletedBy = actor.id,
            deleteReason = payload.deleteReason,
          )
        }
      }

    if (completed) {
      val deletedAt = OffsetDateTime.ofInstant(runtime.clock.instant(), ZoneOffset.UTC)
      runtime.domainEventPublisher.publish(
        spec = ProjectDomainEvents.Destroyed,
        key = payload.projectId,
        payload =
          ProjectDestroyedEvent.from(
            project = project,
            tenantPublicId = payload.tenantId,
            deletedAt = deletedAt,
            deleteReason = payload.deleteReason,
          ),
        metadata = EventMetadata(tenantId = payload.tenantId),
      )
    }
  }
}
