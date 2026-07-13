package ink.doa.workbench.application.jobs.messaging

import ink.doa.workbench.kernel.messaging.DomainEventDecoder
import ink.doa.workbench.kernel.messaging.DomainEventEnvelope
import ink.doa.workbench.tenant.tenant.events.TenantCreatedEvent
import ink.doa.workbench.tenant.tenant.events.TenantDestroyRequestedEvent
import ink.doa.workbench.tenant.tenant.events.TenantDomainEvents
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@ink.doa.workbench.application.jobs.JobsEnabled
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
