package ink.doa.workbench.worker.sprint

import ink.doa.workbench.core.messaging.DomainEventDecoder
import ink.doa.workbench.core.messaging.DomainTopics
import ink.doa.workbench.core.sprint.events.SprintDomainEvents
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class SprintEventListener(
  private val decoder: DomainEventDecoder,
  private val handler: SprintCloseRequestedEventHandler,
) {
  @KafkaListener(topics = [DomainTopics.SPRINT])
  fun onMessage(record: ConsumerRecord<String, String>) {
    runBlocking {
      val envelope = decoder.parseEnvelope(record.value())
      if (envelope.type == SprintDomainEvents.CloseRequested.type) {
        handler.handle(decoder.decode(SprintDomainEvents.CloseRequested, envelope))
      }
    }
  }
}
