package doa.ink.workbench.data.messaging

import doa.ink.workbench.core.port.messaging.DomainEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class KafkaDomainEventPublisher(private val kafkaTemplate: KafkaTemplate<String, String>?) :
  DomainEventPublisher {
  private val logger = LoggerFactory.getLogger(javaClass)

  override fun publish(topic: String, key: String, payload: String) {
    if (kafkaTemplate == null) {
      logger.info("domain_event_skipped topic={} key={} reason=kafka_template_missing", topic, key)
      return
    }
    kafkaTemplate.send(topic, key, payload)
  }
}
