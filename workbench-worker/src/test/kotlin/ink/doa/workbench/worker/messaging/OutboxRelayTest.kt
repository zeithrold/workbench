package ink.doa.workbench.worker.messaging

import ink.doa.workbench.core.port.messaging.ClaimedOutboxPublication
import ink.doa.workbench.core.port.messaging.MessagingBackend
import ink.doa.workbench.core.port.messaging.OutboxPublicationStore
import ink.doa.workbench.jobs.messaging.DomainEventExecutionService
import ink.doa.workbench.jobs.messaging.MessagingProperties
import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import java.util.concurrent.CompletableFuture
import org.springframework.kafka.core.KafkaTemplate

class OutboxRelayTest :
  StringSpec({
    "publishes a locator and records publication" {
      val repository = mockk<OutboxPublicationStore>(relaxed = true)
      val execution = mockk<DomainEventExecutionService>(relaxed = true)
      val kafka = mockk<KafkaTemplate<String, String>>()
      val message = ClaimedOutboxPublication(UUID.randomUUID(), "topic", "key", 0)
      every { repository.claim(any(), any(), any(), any(), any()) } returns listOf(message)
      every { kafka.send(message.topic, message.partitionKey, any()) } returns
        CompletableFuture.completedFuture(null)

      OutboxRelay(repository, execution, MessagingProperties(epoch = "deploy-1"), kafka).relay()

      verify {
        repository.markPublished(message.outboxId, MessagingBackend.KAFKA, "deploy-1", any())
      }
      verify { kafka.send(message.topic, message.partitionKey, match { !it.contains("payload") }) }
    }

    "failed locator publication is retried in PostgreSQL" {
      val repository = mockk<OutboxPublicationStore>(relaxed = true)
      val execution = mockk<DomainEventExecutionService>(relaxed = true)
      val kafka = mockk<KafkaTemplate<String, String>>()
      val message = ClaimedOutboxPublication(UUID.randomUUID(), "topic", "key", 2)
      every { repository.claim(any(), any(), any(), any(), any()) } returns listOf(message)
      every { kafka.send(message.topic, message.partitionKey, any()) } throws
        IllegalStateException("broker unavailable")

      OutboxRelay(repository, execution, MessagingProperties(epoch = "deploy-1"), kafka).relay()

      verify {
        repository.markFailed(match { it.outboxId == message.outboxId && it.attempts == 3 })
      }
    }

    "exhausted locator publication is persisted dead" {
      val repository = mockk<OutboxPublicationStore>(relaxed = true)
      val kafka = mockk<KafkaTemplate<String, String>>()
      val message = ClaimedOutboxPublication(UUID.randomUUID(), "topic", "key", 7)
      every { repository.claim(any(), any(), any(), any(), any()) } returns listOf(message)
      every { kafka.send(any<String>(), any<String>(), any<String>()) } throws
        IllegalStateException("broker unavailable")

      OutboxRelay(
          repository,
          mockk<DomainEventExecutionService>(relaxed = true),
          MessagingProperties(epoch = "deploy-1"),
          kafka,
        )
        .relay()

      verify { repository.markFailed(match { it.attempts == 8 }) }
    }
  })
