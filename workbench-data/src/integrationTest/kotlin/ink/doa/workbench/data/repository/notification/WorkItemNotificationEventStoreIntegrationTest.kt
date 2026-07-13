package ink.doa.workbench.data.repository.notification

import ink.doa.workbench.notification.CreateNotificationCommand
import ink.doa.workbench.notification.NotificationChannel
import ink.doa.workbench.notification.NotificationPreferenceRecord
import ink.doa.workbench.testsupport.postgres.MigrationSpec
import ink.doa.workbench.testsupport.postgres.WorkbenchPostgresTestSupport
import ink.doa.workbench.testsupport.postgres.jdbcTemplate
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.JsonObject

class WorkItemNotificationEventStoreIntegrationTest :
  StringSpec({
    "claim is idempotent" {
      WorkbenchPostgresTestSupport.openDatabase(MigrationSpec.Full).use { lease ->
        val repository = ExposedNotificationRepository(lease.database)
        val jdbc = lease.jdbcTemplate()

        repository.processIfUnprocessed("test-consumer", "evt_1", null)
        repository.processIfUnprocessed("test-consumer", "evt_1", null)

        jdbc.queryForObject(
          "SELECT count(*) FROM processed_domain_events WHERE consumer_name = ? AND event_id = ?",
          Int::class.java,
          "test-consumer",
          "evt_1",
        ) shouldBe 1
      }
    }

    "notification failure rolls back the event claim" {
      WorkbenchPostgresTestSupport.openDatabase(MigrationSpec.Full).use { lease ->
        val repository = ExposedNotificationRepository(lease.database)
        val jdbc = lease.jdbcTemplate()
        val eventId = "evt_rollback"
        val command =
          CreateNotificationCommand(
            recipientUserId = UUID.randomUUID(),
            tenantId = UUID.randomUUID(),
            projectId = null,
            workItemId = null,
            sourceEventId = eventId,
            notificationType = "work_item.updated",
            title = "title",
            body = "body",
            payload = JsonObject(emptyMap()),
          )

        shouldThrowAny {
          repository.processIfUnprocessed("test-consumer", eventId, command)
        }

        jdbc.queryForObject(
          "SELECT count(*) FROM processed_domain_events WHERE consumer_name = ? AND event_id = ?",
          Int::class.java,
          "test-consumer",
          eventId,
        ) shouldBe 0
      }
    }

    "notification store lists, counts, and marks notifications read" {
      WorkbenchPostgresTestSupport.openDatabase(MigrationSpec.Full).use { lease ->
        val stack = ink.doa.workbench.data.support.seedWorkItemStack(lease.database)
        val repository = ExposedNotificationRepository(lease.database)
        val command =
          CreateNotificationCommand(
            recipientUserId = stack.actorId,
            tenantId = stack.tenantId,
            projectId = stack.projectId,
            workItemId = null,
            sourceEventId = "evt_read",
            notificationType = "work_item.updated",
            title = "title",
            body = "body",
            payload = JsonObject(emptyMap()),
            channels = setOf(NotificationChannel.IN_APP),
          )
        val created = repository.create(command)
        repository.create(command).id shouldBe created.id
        repository.unreadCount(stack.actorId, stack.tenantId) shouldBe 1
        repository.processIfUnprocessed("test-consumer", "evt_read", command)?.id shouldBe
          created.id
        repository.processIfUnprocessed("test-consumer", "evt_read", command).shouldBeNull()
        repository.list(stack.actorId, stack.tenantId, 1, -5).single().apiId shouldBe created.apiId
        repository.markRead(
          stack.actorId,
          stack.tenantId,
          created.apiId.value,
          OffsetDateTime.now(),
        ) shouldBe true
        repository.markRead(
          stack.actorId,
          stack.tenantId,
          created.apiId.value,
          OffsetDateTime.now(),
        ) shouldBe false
        repository.unreadCount(stack.actorId, stack.tenantId) shouldBe 0
        repository.markAllRead(stack.actorId, stack.tenantId, OffsetDateTime.now()) shouldBe 0
      }
    }

    "notification preferences are inserted, updated, and listed" {
      WorkbenchPostgresTestSupport.openDatabase(MigrationSpec.Full).use { lease ->
        val stack = ink.doa.workbench.data.support.seedWorkItemStack(lease.database)
        val repository = ExposedNotificationRepository(lease.database)
        val preference =
          NotificationPreferenceRecord(stack.actorId, "work_item.updated", true, false)
        repository.getPreference(stack.actorId, preference.notificationType).shouldBeNull()
        repository.upsertPreference(preference) shouldBe preference
        repository.getPreference(stack.actorId, preference.notificationType) shouldBe preference
        repository.upsertPreference(preference.copy(emailEnabled = true))
        repository.listPreferences(stack.actorId).single().emailEnabled shouldBe true
      }
    }
  })
