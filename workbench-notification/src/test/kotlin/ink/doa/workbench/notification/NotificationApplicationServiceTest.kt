package ink.doa.workbench.notification

import ink.doa.workbench.kernel.common.ids.PublicId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.JsonObject

class NotificationApplicationServiceTest :
  StringSpec({
    val userId = UUID.randomUUID()
    val tenantId = UUID.randomUUID()
    val command =
      CreateNotificationCommand(
        recipientUserId = userId,
        tenantId = tenantId,
        projectId = null,
        workItemId = null,
        sourceEventId = "evt-1",
        notificationType = "work_item.updated",
        title = "Updated",
        body = "Body",
        payload = JsonObject(emptyMap()),
      )
    val record =
      NotificationRecord(
        id = UUID.randomUUID(),
        apiId = PublicId.new("ntf"),
        recipientUserId = userId,
        tenantId = tenantId,
        projectId = null,
        workItemId = null,
        sourceEventId = command.sourceEventId,
        notificationType = command.notificationType,
        title = command.title,
        body = command.body,
        payload = command.payload,
        readAt = null,
        createdAt = Instant.parse("2026-01-01T00:00:00Z").atOffset(ZoneOffset.UTC),
      )

    fun service(
      repository: NotificationStore = mockk(),
      workItemEvents: WorkItemNotificationEventStore = mockk(),
    ) =
      NotificationApplicationService(
        repository,
        workItemEvents,
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
      )

    "delegates notification queries and maps records to views" {
      val repository = mockk<NotificationStore>()
      coEvery { repository.list(userId, tenantId, 10, 2) } returns listOf(record)
      coEvery { repository.unreadCount(userId, tenantId) } returns 3
      val service = service(repository)

      service.list(userId, tenantId, 10, 2).single().id shouldBe record.apiId.value
      service.unreadCount(userId, tenantId) shouldBe 3
    }

    "applies preferences before create and event processing" {
      val repository = mockk<NotificationStore>()
      val events = mockk<WorkItemNotificationEventStore>()
      coEvery { repository.getPreference(userId, command.notificationType) } returns
        NotificationPreferenceRecord(userId, command.notificationType, false, true)
      coEvery { repository.create(any()) } returns record
      coEvery { events.processIfUnprocessed(any(), any(), any()) } returns record
      val service = service(repository, events)

      service.create(command)
      service.processWorkItemEvent("consumer", command.sourceEventId, command)
      coVerify(exactly = 2) {
        repository.getPreference(userId, command.notificationType)
      }
      coVerify {
        repository.create(match { it.channels == setOf(NotificationChannel.EMAIL) })
        events.processIfUnprocessed(
          "consumer",
          command.sourceEventId,
          match { it.channels == setOf(NotificationChannel.EMAIL) },
        )
      }
    }

    "claims an event without creating a notification when command is absent" {
      val events = mockk<WorkItemNotificationEventStore>()
      coEvery { events.processIfUnprocessed("consumer", "evt-empty", null) } returns null

      service(workItemEvents = events).processWorkItemEvent("consumer", "evt-empty", null) shouldBe
        null
      coVerify(exactly = 1) { events.processIfUnprocessed("consumer", "evt-empty", null) }
    }

    "updates read state and preferences" {
      val repository = mockk<NotificationStore>()
      val preference = NotificationPreferenceRecord(userId, command.notificationType, true, false)
      coEvery { repository.markRead(userId, tenantId, record.apiId.value, any()) } returns true
      coEvery { repository.markAllRead(userId, tenantId, any()) } returns 2
      coEvery { repository.upsertPreference(preference) } returns preference
      coEvery { repository.listPreferences(userId) } returns listOf(preference)
      val service = service(repository)

      service.markRead(userId, tenantId, record.apiId.value) shouldBe true
      service.markAllRead(userId, tenantId) shouldBe 2
      service.updatePreference(userId, command.notificationType, true, false) shouldBe preference
      service.listPreferences(userId) shouldContainExactly listOf(preference)
    }
  })
