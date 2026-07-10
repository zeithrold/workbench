package ink.doa.workbench.service.notification

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.notification.CreateNotificationCommand
import ink.doa.workbench.core.notification.NotificationChannel
import ink.doa.workbench.core.notification.NotificationPreferenceRecord
import ink.doa.workbench.core.notification.NotificationRecord
import ink.doa.workbench.core.notification.NotificationStore
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

class NotificationApplicationServiceTest :
  StringSpec({
    val repository = mockk<NotificationStore>()
    val clock = Clock.fixed(Instant.parse("2026-07-10T12:00:00Z"), ZoneOffset.UTC)
    val service = NotificationApplicationService(repository, clock)
    val userId = UUID.randomUUID()
    val tenantId = UUID.randomUUID()
    val now = OffsetDateTime.now(clock)
    val notification =
      NotificationRecord(
        id = UUID.randomUUID(),
        apiId = PublicId.new("ntf"),
        recipientUserId = userId,
        tenantId = tenantId,
        projectId = null,
        workItemId = null,
        sourceEventId = "evt_1",
        notificationType = "work_item.assigned",
        title = "Assigned",
        body = "You were assigned",
        payload = JsonObject(emptyMap()),
        readAt = null,
        createdAt = now,
      )

    "list maps repository records to views" {
      coEvery { repository.list(userId, tenantId, 20, 0) } returns listOf(notification)

      runBlocking {
        val views = service.list(userId, tenantId, 20, 0)
        views.single().id shouldBe notification.apiId.value
        views.single().notificationType shouldBe "work_item.assigned"
      }
    }

    "unreadCount delegates to repository" {
      coEvery { repository.unreadCount(userId, tenantId) } returns 3

      runBlocking { service.unreadCount(userId, tenantId) shouldBe 3 }
    }

    "markRead passes fixed clock time to repository" {
      coEvery { repository.markRead(userId, tenantId, notification.apiId.value, now) } returns true

      runBlocking {
        service.markRead(userId, tenantId, notification.apiId.value) shouldBe true
      }
      coVerify { repository.markRead(userId, tenantId, notification.apiId.value, now) }
    }

    "markAllRead passes fixed clock time to repository" {
      coEvery { repository.markAllRead(userId, tenantId, now) } returns 2

      runBlocking { service.markAllRead(userId, tenantId) shouldBe 2 }
    }

    "create filters channels using user preferences" {
      val command =
        CreateNotificationCommand(
          recipientUserId = userId,
          tenantId = tenantId,
          projectId = null,
          workItemId = null,
          sourceEventId = "evt_1",
          notificationType = "work_item.assigned",
          title = "Assigned",
          body = "Body",
          payload = JsonObject(emptyMap()),
          channels = setOf(NotificationChannel.IN_APP, NotificationChannel.EMAIL),
        )
      coEvery { repository.getPreference(userId, "work_item.assigned") } returns
        NotificationPreferenceRecord(
          userId = userId,
          notificationType = "work_item.assigned",
          inAppEnabled = true,
          emailEnabled = false,
        )
      coEvery {
        repository.create(command.copy(channels = setOf(NotificationChannel.IN_APP)))
      } returns notification

      runBlocking { service.create(command) shouldBe notification }
    }

    "create keeps default channels when preference is missing" {
      val command =
        CreateNotificationCommand(
          recipientUserId = userId,
          tenantId = tenantId,
          projectId = null,
          workItemId = null,
          sourceEventId = "evt_2",
          notificationType = "work_item.updated",
          title = "Updated",
          body = "Body",
          payload = JsonObject(emptyMap()),
        )
      coEvery { repository.getPreference(userId, "work_item.updated") } returns null
      coEvery {
        repository.create(
          command.copy(channels = setOf(NotificationChannel.IN_APP, NotificationChannel.EMAIL))
        )
      } returns notification

      runBlocking { service.create(command) shouldBe notification }
    }

    "listPreferences and updatePreference delegate to repository" {
      val preference =
        NotificationPreferenceRecord(
          userId = userId,
          notificationType = "work_item.assigned",
          inAppEnabled = false,
          emailEnabled = true,
        )
      coEvery { repository.listPreferences(userId) } returns listOf(preference)
      coEvery { repository.upsertPreference(preference) } returns preference

      runBlocking {
        service.listPreferences(userId) shouldBe listOf(preference)
        service.updatePreference(userId, "work_item.assigned", false, true) shouldBe preference
      }
    }
  })
