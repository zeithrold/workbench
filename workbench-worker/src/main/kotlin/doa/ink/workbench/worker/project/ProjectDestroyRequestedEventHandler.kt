package doa.ink.workbench.worker.project

import doa.ink.workbench.core.identity.TenantRepository
import doa.ink.workbench.core.identity.UserRepository
import doa.ink.workbench.core.messaging.EventMetadata
import doa.ink.workbench.core.port.locking.DistributedLockService
import doa.ink.workbench.core.port.messaging.DomainEventPublisher
import doa.ink.workbench.core.project.ProjectRepository
import doa.ink.workbench.core.project.events.ProjectDestroyRequestedEvent
import doa.ink.workbench.core.project.events.ProjectDestroyedEvent
import doa.ink.workbench.core.project.events.ProjectDomainEvents
import doa.ink.workbench.service.project.ProjectDestructionService
import doa.ink.workbench.worker.messaging.DomainEventHandler
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ProjectDestroyRequestedEventHandler(
  private val tenants: TenantRepository,
  private val projects: ProjectRepository,
  private val users: UserRepository,
  private val projectDestructionService: ProjectDestructionService,
  private val domainEventPublisher: DomainEventPublisher,
  private val distributedLockService: DistributedLockService,
  private val clock: Clock,
) : DomainEventHandler<ProjectDestroyRequestedEvent> {
  private val logger = LoggerFactory.getLogger(javaClass)

  @Suppress("ReturnCount")
  override suspend fun handle(payload: ProjectDestroyRequestedEvent) {
    val tenant =
      tenants.findByApiId(payload.tenantId)
        ?: run {
          logger.warn(
            "project_destroy_requested_skipped tenantId={} reason=tenant_not_found",
            payload.tenantId,
          )
          return
        }
    val project =
      projects.findByApiId(tenant.id, payload.projectId)
        ?: run {
          logger.warn(
            "project_destroy_requested_skipped projectId={} reason=project_not_found",
            payload.projectId,
          )
          return
        }

    val actor =
      users.findByApiId(payload.requestedBy)
        ?: run {
          logger.warn(
            "project_destroy_requested_skipped projectId={} reason=actor_not_found actor={}",
            payload.projectId,
            payload.requestedBy,
          )
          return
        }

    val completed =
      distributedLockService.withLock(
        name = "project-destroy:${project.id}",
        wait = Duration.ofSeconds(5),
        lease = Duration.ofMinutes(10),
      ) {
        runBlocking {
          projectDestructionService.execute(
            tenantId = tenant.id,
            projectId = project.id,
            deletedBy = actor.id,
            deleteReason = payload.deleteReason,
          )
        }
      }

    if (completed) {
      val deletedAt = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
      domainEventPublisher.publish(
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
