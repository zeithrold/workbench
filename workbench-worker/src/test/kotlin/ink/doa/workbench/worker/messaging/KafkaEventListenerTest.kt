package ink.doa.workbench.worker.messaging

import ink.doa.workbench.jobs.messaging.DomainEventExecutionService
import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeader

class KafkaEventListenerTest :
  StringSpec({
    "listener resolves the Debezium outbox id header through the execution service" {
      val execution = mockk<DomainEventExecutionService>()
      val id = UUID.randomUUID()
      every { execution.executeLocator(id) } returns 1

      val record = ConsumerRecord("topic", 0, 1, "key", "{\"type\":\"work_item.updated\"}")
      record.headers().add(RecordHeader("id", id.toString().toByteArray()))

      KafkaEventListener(execution).onMessage(record)

      verify { execution.executeLocator(id) }
    }

    "listener rejects an event without the Debezium id header" {
      val execution = mockk<DomainEventExecutionService>(relaxed = true)

      io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
        KafkaEventListener(execution).onMessage(ConsumerRecord("topic", 0, 1, "key", "{}"))
      }
    }

    "listener rejects a malformed Debezium id header" {
      val execution = mockk<DomainEventExecutionService>(relaxed = true)
      val record = ConsumerRecord("topic", 0, 1, "key", "{}")
      record.headers().add(RecordHeader("id", "not-a-uuid".toByteArray()))

      io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
        KafkaEventListener(execution).onMessage(record)
      }
    }
  })
