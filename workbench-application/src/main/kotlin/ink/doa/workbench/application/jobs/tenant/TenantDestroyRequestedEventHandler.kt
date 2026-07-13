package ink.doa.workbench.application.jobs.tenant

import ink.doa.workbench.application.jobs.messaging.DomainEventHandler
import ink.doa.workbench.identity.UserRepository
import ink.doa.workbench.kernel.messaging.EventMetadata
import ink.doa.workbench.kernel.port.locking.DistributedLockService
import ink.doa.workbench.kernel.port.messaging.DomainEventPublisher
import ink.doa.workbench.tenant.TenantRepository
import ink.doa.workbench.tenant.instance.TenantDestructionService
import ink.doa.workbench.tenant.tenant.events.TenantDestroyRequestedEvent
import ink.doa.workbench.tenant.tenant.events.TenantDestroyedEvent
import ink.doa.workbench.tenant.tenant.events.TenantDomainEvents
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@ink.doa.workbench.application.jobs.JobsEnabled
@Component
class TenantDestroyRequestedEventHandler(
  private val tenants: TenantRepository,
  private val users: UserRepository,
  private val tenantDestructionService: TenantDestructionService,
  private val domainEventPublisher: DomainEventPublisher,
  private val distributedLockService: DistributedLockService,
  private val clock: Clock,
) : DomainEventHandler<TenantDestroyRequestedEvent> {
  private val logger = LoggerFactory.getLogger(javaClass)

  override suspend fun handle(payload: TenantDestroyRequestedEvent) {
    val tenant =
      tenants.findByApiIdForAdmin(payload.tenantId)
        ?: run {
          logger.warn(
            "tenant_destroy_requested_skipped tenantId={} reason=tenant_not_found",
            payload.tenantId,
          )
          return
        }

    val actor =
      users.findByApiId(payload.requestedBy)
        ?: run {
          logger.warn(
            "tenant_destroy_requested_skipped tenantId={} reason=actor_not_found actor={}",
            payload.tenantId,
            payload.requestedBy,
          )
          return
        }

    val completed =
      distributedLockService.withLock(
        name = "tenant-destroy:${tenant.id}",
        wait = Duration.ofSeconds(5),
        lease = Duration.ofMinutes(10),
      ) {
        runBlocking {
          tenantDestructionService.execute(
            tenantId = tenant.id,
            deletedBy = actor.id,
            deleteReason = payload.deleteReason,
          )
        }
      }

    if (completed) {
      val deletedAt = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
      domainEventPublisher.publish(
        spec = TenantDomainEvents.Destroyed,
        key = payload.tenantId,
        payload =
          TenantDestroyedEvent.from(
            tenant = tenant,
            deletedAt = deletedAt,
            deleteReason = payload.deleteReason,
          ),
        metadata = EventMetadata(tenantId = payload.tenantId),
      )
    }
  }
}
