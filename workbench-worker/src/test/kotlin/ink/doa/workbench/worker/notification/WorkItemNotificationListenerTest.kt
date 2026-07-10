package ink.doa.workbench.worker.notification

import ink.doa.workbench.core.messaging.DomainEventDecoder
import io.kotest.core.spec.style.StringSpec
import io.mockk.coVerify
import io.mockk.mockk
import org.apache.kafka.clients.consumer.ConsumerRecord

class WorkItemNotificationListenerTest :
  StringSpec({
    "unknown work item event types are ignored" {
      val handler = mockk<WorkItemNotificationHandler>(relaxed = true)
      val listener = WorkItemNotificationListener(DomainEventDecoder(), handler)
      val record =
        ConsumerRecord(
          "work-items",
          0,
          1L,
          "key",
          """{"eventId":"evt-1","type":"work_item.deleted","occurredAt":"2026-01-01T00:00:00Z","payload":{}}""",
        )

      listener.onMessage(record)

      coVerify(exactly = 0) { handler.handle(any(), any(), any()) }
    }
  })
