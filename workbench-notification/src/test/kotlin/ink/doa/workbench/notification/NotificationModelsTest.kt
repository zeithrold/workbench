package ink.doa.workbench.notification

import ink.doa.workbench.kernel.common.ids.PublicId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.JsonObject

class NotificationModelsTest :
  StringSpec({
    val now = OffsetDateTime.of(2026, 7, 11, 0, 0, 0, 0, ZoneOffset.UTC)
    val notificationId = UUID.randomUUID()
    val tenantId = UUID.randomUUID()
    val userId = UUID.randomUUID()

    "create notification command defaults to both delivery channels" {
      val command =
        CreateNotificationCommand(
          recipientUserId = userId,
          tenantId = tenantId,
          projectId = null,
          workItemId = null,
          sourceEventId = "evt_1",
          notificationType = "work_item.updated",
          title = "Updated",
          body = "The item changed",
          payload = JsonObject(emptyMap()),
        )

      command.channels shouldBe setOf(NotificationChannel.IN_APP, NotificationChannel.EMAIL)
    }

    "notification records retain delivery and read state" {
      val record =
        NotificationRecord(
          id = notificationId,
          apiId = PublicId("ntf_01JABCDEFGHJKMNPQRSTVWXYZ0"),
          recipientUserId = userId,
          tenantId = tenantId,
          projectId = null,
          workItemId = null,
          sourceEventId = "evt_1",
          notificationType = "work_item.updated",
          title = "Updated",
          body = "The item changed",
          payload = JsonObject(emptyMap()),
          readAt = null,
          createdAt = now,
        )
      val delivery =
        NotificationDeliveryRecord(
          notificationId = notificationId,
          channel = NotificationChannel.EMAIL,
          status = NotificationDeliveryStatus.RETRY,
          attempts = 2,
          nextAttemptAt = now,
          sentAt = null,
          lastError = "smtp unavailable",
        )
      val preference =
        NotificationPreferenceRecord(
          userId = userId,
          notificationType = record.notificationType,
          inAppEnabled = true,
          emailEnabled = false,
        )

      record.readAt shouldBe null
      delivery.status shouldBe NotificationDeliveryStatus.RETRY
      preference.emailEnabled shouldBe false
    }
  })
