package ink.doa.workbench.web.notification

import ink.doa.workbench.core.notification.NotificationPreferenceRecord
import ink.doa.workbench.service.notification.NotificationView
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.JsonObject

class NotificationResponsesTest :
  StringSpec({
    "notification response maps identifiers and content" {
      val response =
        NotificationResponse.from(
          NotificationView(
            id = "ntf_1",
            recipientUserId = UUID.randomUUID(),
            tenantId = UUID.randomUUID(),
            projectId = UUID.randomUUID(),
            workItemId = UUID.randomUUID(),
            notificationType = "work_item.updated",
            title = "Title",
            body = "Body",
            payload = JsonObject(emptyMap()),
            readAt = null,
            createdAt = OffsetDateTime.now(ZoneOffset.UTC),
          )
        )
      response.id shouldBe "ntf_1"
      response.projectId.shouldNotBeNull()
      response.workItemId.shouldNotBeNull()
      response.notificationType shouldBe "work_item.updated"
      response.title shouldBe "Title"
      response.body shouldBe "Body"
    }

    "notification preference response maps settings" {
      val response =
        NotificationPreferenceResponse.from(
          NotificationPreferenceRecord(UUID.randomUUID(), "work_item.updated", false, true)
        )
      response.notificationType shouldBe "work_item.updated"
      response.inAppEnabled shouldBe false
      response.emailEnabled shouldBe true
    }

    "unread count response stores count" {
      UnreadCountResponse(4).count shouldBe 4
      UpdateNotificationPreferenceRequest("work_item.updated").emailEnabled shouldBe true
    }
  })
