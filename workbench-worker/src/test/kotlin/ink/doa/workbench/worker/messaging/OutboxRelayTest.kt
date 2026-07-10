package ink.doa.workbench.worker.messaging

import ink.doa.workbench.data.messaging.OutboxMessage
import ink.doa.workbench.data.messaging.OutboxRelayRepository
import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import java.util.concurrent.CompletableFuture
import org.springframework.kafka.core.KafkaTemplate

class OutboxRelayTest :
  StringSpec({
    "published outbox message is marked published" {
      val repository = mockk<OutboxRelayRepository>()
      val kafka = mockk<KafkaTemplate<String, String>>()
      val message = OutboxMessage(UUID.randomUUID(), "work-item", "key", "payload", 0)
      every { repository.claim(any(), any(), any()) } returns listOf(message)
      every { kafka.send(message.topic, message.partitionKey, message.payload) } returns
        CompletableFuture.completedFuture(null)
      every { repository.markPublished(message.id, any()) } returns Unit

      OutboxRelay(repository, kafka).relay()

      verify { repository.markPublished(message.id, any()) }
    }

    "failed publish is marked failed with exponential retry" {
      val repository = mockk<OutboxRelayRepository>()
      val kafka = mockk<KafkaTemplate<String, String>>()
      val message = OutboxMessage(UUID.randomUUID(), "work-item", "key", "payload", 2)
      every { repository.claim(any(), any(), any()) } returns listOf(message)
      every { kafka.send(message.topic, message.partitionKey, message.payload) } throws
        IllegalStateException("broker unavailable")
      every { repository.markFailed(any(), any(), any(), any()) } returns Unit

      OutboxRelay(repository, kafka).relay()

      verify { repository.markFailed(message.id, 3, any(), "broker unavailable") }
    }
  })
