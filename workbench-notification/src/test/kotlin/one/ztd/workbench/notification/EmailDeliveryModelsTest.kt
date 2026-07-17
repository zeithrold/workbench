package one.ztd.workbench.notification

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.UUID
import one.ztd.workbench.notification.port.PendingEmailDelivery

class EmailDeliveryModelsTest :
  StringSpec({
    "email delivery records retain transport-neutral content" {
      val deliveryId = UUID.randomUUID()
      val notificationId = UUID.randomUUID()
      val pending =
        PendingEmailDelivery(
          deliveryId = deliveryId,
          notificationId = notificationId,
          recipient = "ada@example.test",
          subject = "Assigned",
          body = "Issue WB-1 was assigned to you.",
          attempts = 2,
        )

      pending.deliveryId shouldBe deliveryId
      pending.notificationId shouldBe notificationId
      pending.recipient shouldBe "ada@example.test"
      pending.subject shouldBe "Assigned"
      pending.body shouldBe "Issue WB-1 was assigned to you."
      pending.attempts shouldBe 2
    }

    "email message retains sender input" {
      EmailMessage("ada@example.test", "Assigned", "Body") shouldBe
        EmailMessage("ada@example.test", "Assigned", "Body")
    }

    "module configuration can be constructed explicitly" {
      NotificationModuleConfiguration()::class shouldBe NotificationModuleConfiguration::class
    }
  })
