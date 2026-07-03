package doa.ink.workbench.worker.messaging

import doa.ink.workbench.core.messaging.DomainTopics
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class TenantEventListener(private val pipeline: ConsumerPipeline) {
  @KafkaListener(topics = [DomainTopics.TENANT])
  fun onMessage(record: ConsumerRecord<String, String>) {
    pipeline.run(record)
  }
}
