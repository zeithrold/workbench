package ink.doa.workbench.worker.messaging

import ink.doa.workbench.core.messaging.OutboxLocator
import ink.doa.workbench.jobs.messaging.DomainEventExecutionService
import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import org.apache.kafka.clients.consumer.ConsumerRecord

class KafkaEventListenerTest :
  StringSpec({
    "listener resolves a pointer through the shared execution service" {
      val execution = mockk<DomainEventExecutionService>()
      val id = UUID.randomUUID()
      every { execution.executeLocator(id) } returns 1

      KafkaEventListener(execution)
        .onMessage(ConsumerRecord("topic", 0, 1, "key", OutboxLocator.encode(id, "deploy-1")))

      verify { execution.executeLocator(id) }
    }
  })
