package ink.doa.workbench.data.messaging

import ink.doa.workbench.core.messaging.DomainEventEncoder
import ink.doa.workbench.core.messaging.DomainEventSpec
import ink.doa.workbench.core.messaging.EventMetadata
import ink.doa.workbench.core.port.messaging.DomainEventOutbox
import ink.doa.workbench.core.port.messaging.DomainEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class KafkaDomainEventPublisher(
  private val encoder: DomainEventEncoder,
  private val kafkaTemplate: KafkaTemplate<String, String>?,
  private val outbox: DomainEventOutbox? = null,
) : DomainEventPublisher {
  private val logger = LoggerFactory.getLogger(javaClass)

  override fun <T : Any> publish(
    spec: DomainEventSpec<T>,
    key: String,
    payload: T,
    metadata: EventMetadata,
  ) {
    if (outbox != null) {
      outbox.append(spec, key, payload, metadata)
      logger.info("domain_event_enqueued topic={} type={} key={}", spec.topic, spec.type, key)
      return
    }
    if (kafkaTemplate == null) {
      logger.info(
        "domain_event_skipped topic={} type={} key={} reason=kafka_template_missing",
        spec.topic,
        spec.type,
        key,
      )
      return
    }
    val encoded = encoder.encode(spec, payload, metadata)
    kafkaTemplate.send(spec.topic, key, encoded)
    logger.info(
      "domain_event_published topic={} type={} key={}",
      spec.topic,
      spec.type,
      key,
    )
  }
}
