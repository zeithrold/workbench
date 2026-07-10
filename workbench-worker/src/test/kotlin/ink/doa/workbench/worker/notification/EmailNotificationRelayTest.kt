package ink.doa.workbench.worker.notification

import ink.doa.workbench.core.notification.EmailMessage
import ink.doa.workbench.core.notification.EmailSender
import ink.doa.workbench.data.notification.NotificationDeliveryRepository
import ink.doa.workbench.data.notification.PendingEmailDelivery
import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID

class EmailNotificationRelayTest :
  StringSpec({
    val repository = mockk<NotificationDeliveryRepository>()
    val sender = mockk<EmailSender>()
    val relay = EmailNotificationRelay(repository, sender)
    val deliveryId = UUID.randomUUID()
    val delivery =
      PendingEmailDelivery(
        deliveryId = deliveryId,
        notificationId = UUID.randomUUID(),
        recipient = "ada@example.test",
        subject = "Assigned",
        body = "You were assigned",
        attempts = 0,
      )

    "relay sends claimed emails and marks them sent" {
      every { repository.claimEmails(25, any(), any()) } returns listOf(delivery)
      every {
        sender.send(EmailMessage(delivery.recipient, delivery.subject, delivery.body))
      } returns Unit
      every { repository.markSent(deliveryId, any()) } returns Unit

      relay.relay()

      verify { repository.markSent(deliveryId, any()) }
    }

    "relay marks failed when sender throws" {
      every { repository.claimEmails(25, any(), any()) } returns listOf(delivery)
      every { sender.send(any()) } throws IllegalStateException("smtp down")
      every { repository.markFailed(deliveryId, 1, any(), "smtp down") } returns Unit

      relay.relay()

      verify { repository.markFailed(deliveryId, 1, any(), "smtp down") }
    }
  })
