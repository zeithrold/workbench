package ink.doa.workbench.worker.notification

import ink.doa.workbench.core.messaging.DomainEventDecoder
import ink.doa.workbench.core.messaging.DomainTopics
import ink.doa.workbench.core.workitem.events.WorkItemDomainEvents
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class WorkItemNotificationListener(
  private val decoder: DomainEventDecoder,
  private val handler: WorkItemNotificationHandler,
) {
  @KafkaListener(topics = [DomainTopics.WORK_ITEM])
  fun onMessage(record: ConsumerRecord<String, String>) {
    runBlocking {
      val envelope = decoder.parseEnvelope(record.value())
      val spec =
        when (envelope.type) {
          WorkItemDomainEvents.Created.type -> WorkItemDomainEvents.Created
          WorkItemDomainEvents.Updated.type -> WorkItemDomainEvents.Updated
          WorkItemDomainEvents.Transitioned.type -> WorkItemDomainEvents.Transitioned
          else -> return@runBlocking
        }
      handler.handle(
        eventId = envelope.eventId,
        type = envelope.type,
        payload = decoder.decode(spec, envelope),
      )
    }
  }
}
