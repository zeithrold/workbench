package ink.doa.workbench.jobs.messaging

import ink.doa.workbench.core.messaging.DomainEventDecoder
import ink.doa.workbench.core.messaging.DomainEventEnvelope
import ink.doa.workbench.core.tenant.events.TenantCreatedEvent
import ink.doa.workbench.core.tenant.events.TenantDestroyRequestedEvent
import ink.doa.workbench.core.tenant.events.TenantDomainEvents
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@ink.doa.workbench.jobs.JobsEnabled
@Component
class TenantEventRouter(
  private val decoder: DomainEventDecoder,
  private val tenantCreatedHandler: DomainEventHandler<TenantCreatedEvent>,
  private val tenantDestroyRequestedHandler: DomainEventHandler<TenantDestroyRequestedEvent>,
) {
  private val logger = LoggerFactory.getLogger(javaClass)

  suspend fun route(envelope: DomainEventEnvelope) {
    when (envelope.type) {
      TenantDomainEvents.Created.type -> {
        val event = decoder.decode(TenantDomainEvents.Created, envelope)
        tenantCreatedHandler.handle(event)
      }
      TenantDomainEvents.DestroyRequested.type -> {
        val event = decoder.decode(TenantDomainEvents.DestroyRequested, envelope)
        tenantDestroyRequestedHandler.handle(event)
      }
      else ->
        logger.warn(
          "tenant_event_skipped type={} eventId={} reason=unknown_type",
          envelope.type,
          envelope.eventId,
        )
    }
  }
}
