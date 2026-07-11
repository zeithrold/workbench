package ink.doa.workbench.worker.messaging

import ink.doa.workbench.core.messaging.DomainTopics
import ink.doa.workbench.core.messaging.OutboxLocator
import ink.doa.workbench.jobs.messaging.DomainEventExecutionService
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
    val locator = OutboxLocator.decode(record.value())
    execution.executeLocator(UUID.fromString(locator.outboxId))
  }
}
