package ink.doa.workbench.data.messaging

import ink.doa.workbench.application.messaging.DomainEventOutbox
import ink.doa.workbench.kernel.messaging.DomainEventSpec
import ink.doa.workbench.kernel.messaging.EventMetadata
import ink.doa.workbench.kernel.port.messaging.DomainEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class OutboxDomainEventPublisher(private val outbox: DomainEventOutbox) : DomainEventPublisher {
  private val logger = LoggerFactory.getLogger(javaClass)

  override fun <T : Any> publish(
    spec: DomainEventSpec<T>,
    key: String,
    payload: T,
    metadata: EventMetadata,
  ) {
    outbox.append(spec, key, payload, metadata)
    logger.info("domain_event_enqueued topic={} type={} key={}", spec.topic, spec.type, key)
  }
}
