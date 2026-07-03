package doa.ink.workbench.data.messaging

import doa.ink.workbench.core.messaging.DomainEventEncoder
import doa.ink.workbench.core.messaging.DomainEventSpec
import doa.ink.workbench.core.messaging.EventMetadata
import doa.ink.workbench.core.port.messaging.DomainEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class KafkaDomainEventPublisher(
  private val encoder: DomainEventEncoder,
  private val kafkaTemplate: KafkaTemplate<String, String>?,
) : DomainEventPublisher {
  private val logger = LoggerFactory.getLogger(javaClass)

  override fun <T : Any> publish(
    spec: DomainEventSpec<T>,
    key: String,
    payload: T,
    metadata: EventMetadata,
  ) {
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
