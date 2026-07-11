package ink.doa.workbench.web.messaging

import ink.doa.workbench.core.port.messaging.ClaimedOutboxPublication
import ink.doa.workbench.core.port.messaging.MessagingBackend
import ink.doa.workbench.core.port.messaging.OutboxPublicationStore
import ink.doa.workbench.jobs.messaging.DomainEventDispatcher
import ink.doa.workbench.jobs.messaging.DomainEventExecutionService
import ink.doa.workbench.jobs.messaging.MessagingProperties
import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import org.redisson.api.RStream
import org.redisson.api.RedissonClient
import org.redisson.api.stream.StreamMessageId

class RedisStreamsEmbeddedJobsTest :
  StringSpec({
    "relay stores only an outbox locator in Redis" {
      val redisson = mockk<RedissonClient>()
      val stream = mockk<RStream<String, String>>(relaxed = true)
      val publications = mockk<OutboxPublicationStore>(relaxed = true)
      val execution = mockk<DomainEventExecutionService>(relaxed = true)
      val dispatcher = mockk<DomainEventDispatcher>()
      val message = ClaimedOutboxPublication(UUID.randomUUID(), "topic", "key", 0)
      every { redisson.getStream<String, String>(any<String>()) } returns stream
      every { stream.add(any()) } returns StreamMessageId(1)
      every { publications.claim(any(), any(), any(), any(), any()) } returns listOf(message)
      every { dispatcher.subscriptions() } returns emptyList()

      RedisStreamsEmbeddedJobs(
          redisson,
          publications,
          execution,
          dispatcher,
          MessagingProperties(epoch = "deploy-1"),
        )
        .runOnce()

      verify { stream.add(any()) }
      verify {
        publications.markPublished(
          message.outboxId,
          MessagingBackend.REDIS_STREAMS,
          "deploy-1",
          any(),
        )
      }
    }
  })
