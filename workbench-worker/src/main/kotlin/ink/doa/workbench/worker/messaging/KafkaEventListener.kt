package ink.doa.workbench.worker.messaging

import ink.doa.workbench.application.jobs.messaging.DomainEventExecutionService
import ink.doa.workbench.kernel.messaging.DomainTopics
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["workbench.messaging.transport"], havingValue = "kafka")
class KafkaEventListener(private val execution: DomainEventExecutionService) {
  @KafkaListener(
    topics =
      [DomainTopics.TENANT, DomainTopics.PROJECT, DomainTopics.SPRINT, DomainTopics.WORK_ITEM]
  )
  fun onMessage(record: ConsumerRecord<String, String>) {
    val id =
      record.headers().lastHeader(OUTBOX_ID_HEADER)
        ?: throw IllegalArgumentException("Kafka outbox event is missing the id header")
    execution.executeLocator(
      UUID.fromString(String(id.value(), StandardCharsets.UTF_8).removeSurrounding("\""))
    )
  }

  private companion object {
    const val OUTBOX_ID_HEADER = "id"
  }
}
