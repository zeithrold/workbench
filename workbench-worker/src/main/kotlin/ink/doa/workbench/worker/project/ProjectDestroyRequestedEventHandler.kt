package ink.doa.workbench.worker.project

import ink.doa.workbench.agile.project.ProjectDestructionService
import ink.doa.workbench.core.identity.TenantRepository
import ink.doa.workbench.core.identity.UserRepository
import ink.doa.workbench.core.messaging.EventMetadata
import ink.doa.workbench.core.port.locking.DistributedLockService
import ink.doa.workbench.core.port.messaging.DomainEventPublisher
import ink.doa.workbench.core.project.ProjectRepository
import ink.doa.workbench.core.project.events.ProjectDestroyRequestedEvent
import ink.doa.workbench.core.project.events.ProjectDestroyedEvent
import ink.doa.workbench.core.project.events.ProjectDomainEvents
import ink.doa.workbench.worker.messaging.DomainEventHandler
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
