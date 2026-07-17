package one.ztd.workbench.application.jobs.notification

import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import one.ztd.workbench.notification.EmailSender
import one.ztd.workbench.notification.port.EmailDeliveryStore
import one.ztd.workbench.notification.port.PendingEmailDelivery

class EmailNotificationRelayTest :
  StringSpec({
    "successful email delivery is marked sent" {
      val repository = mockk<EmailDeliveryStore>()
      val sender = mockk<one.ztd.workbench.notification.EmailSender>(relaxed = true)
      val delivery =
        PendingEmailDelivery(
          UUID.randomUUID(),
          UUID.randomUUID(),
          "a@example.test",
          "Subject",
          "Body",
          0,
        )
      every { repository.claimEmails(any(), any(), any()) } returns listOf(delivery)
      every { repository.markSent(any(), any()) } returns Unit

      EmailNotificationRelay(repository, sender).relay()

      verify { sender.send(match { it.recipient == "a@example.test" && it.subject == "Subject" }) }
      verify { repository.markSent(delivery.deliveryId, any()) }
    }

    "failed email delivery is scheduled for retry with error message" {
      val repository = mockk<EmailDeliveryStore>()
      val sender = mockk<EmailSender>()
      val delivery =
        PendingEmailDelivery(
          UUID.randomUUID(),
          UUID.randomUUID(),
          "a@example.test",
          "Subject",
          "Body",
          2,
        )
      every { repository.claimEmails(any(), any(), any()) } returns listOf(delivery)
      every { sender.send(any()) } throws IllegalStateException("smtp unavailable")
      every { repository.markFailed(any(), any(), any(), any()) } returns Unit

      EmailNotificationRelay(repository, sender).relay()

      verify { repository.markFailed(delivery.deliveryId, 3, any(), "smtp unavailable") }
    }
  })
