package ink.doa.workbench.application.jobs.messaging

import ink.doa.workbench.application.messaging.EventSubscription
import ink.doa.workbench.kernel.messaging.DomainEventDecoder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@ink.doa.workbench.application.jobs.JobsEnabled
@Component
class DomainEventDispatcher(
  private val decoder: DomainEventDecoder,
  registrations: List<JobRegistration>,
) {
  private val logger = LoggerFactory.getLogger(javaClass)
  private val registrations = registrations.toList()

  fun subscriptions(): List<EventSubscription> = registrations.map {
    EventSubscription(it.consumerName, it.topic, it.eventTypes)
  }

  suspend fun dispatch(encoded: String, consumerName: String? = null) {
    val envelope = decoder.parseEnvelope(encoded)
    val matching = registrations.filter {
      (consumerName == null || it.consumerName == consumerName) && envelope.type in it.eventTypes
    }
    if (matching.isEmpty()) {
      logger.warn(
        "domain_event_skipped type={} eventId={} reason=no_registration",
        envelope.type,
        envelope.eventId,
      )
      return
    }
    matching.forEach { it.handle(envelope) }
  }
}
