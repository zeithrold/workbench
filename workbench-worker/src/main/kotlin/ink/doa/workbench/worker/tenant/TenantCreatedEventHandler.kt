package ink.doa.workbench.worker.tenant

import ink.doa.workbench.core.tenant.events.TenantCreatedEvent
import ink.doa.workbench.worker.messaging.DomainEventHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

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
