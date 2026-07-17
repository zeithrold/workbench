package one.ztd.workbench.application.jobs.tenant

import one.ztd.workbench.application.jobs.messaging.DomainEventHandler
import one.ztd.workbench.tenant.tenant.events.TenantCreatedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@one.ztd.workbench.application.jobs.JobsEnabled
@Component
class TenantCreatedEventHandler : DomainEventHandler<TenantCreatedEvent> {
  private val logger = LoggerFactory.getLogger(javaClass)

  override suspend fun handle(payload: TenantCreatedEvent) {
    logger.info(
      "tenant_created_received tenantId={} name={} status={}",
      payload.tenantId,
      payload.name,
      payload.status,
    )
  }
}
