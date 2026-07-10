package ink.doa.workbench.worker.notification

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.notification.CreateNotificationCommand
import ink.doa.workbench.core.notification.NotificationRecord
import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.events.WorkItemMutationEvent
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
import ink.doa.workbench.data.messaging.ProcessedDomainEventRepository
import ink.doa.workbench.service.notification.NotificationApplicationService
import io.kotest.core.spec.style.StringSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

class WorkItemNotificationHandlerTest :
  StringSpec({
    val workItems = mockk<WorkItemRepository>()
    val notifications = mockk<NotificationApplicationService>()
    val processed = mockk<ProcessedDomainEventRepository>()
    val handler = WorkItemNotificationHandler(workItems, notifications, processed)

    beforeTest { clearAllMocks(answers = false, recordedCalls = true, childMocks = true) }
    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()
    val assigneeId = UUID.randomUUID()
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val workItem =
      WorkItemRecord(
        id = UUID.randomUUID(),
        apiId = PublicId("wki_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        tenantId = tenantId,
        projectId = projectId,
        issueTypeApiId = PublicId("wit_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        issueTypeConfigApiId = PublicId("wic_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        key = "CORE-1",
        title = "Task",
        description = null,
        statusId = UUID.randomUUID(),
        statusApiId = PublicId("sts_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        statusGroup = WorkItemStatusGroup.IN_PROGRESS,
        reporterId = assigneeId,
        assigneeId = assigneeId,
        priorityApiId = null,
        reporterApiId = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        assigneeApiId = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        sprintApiId = null,
        properties = JsonObject(emptyMap()),
        createdAt = now,
        updatedAt = now,
      )
    val payload =
      WorkItemMutationEvent(
        tenantId = tenantId.toString(),
        projectId = projectId.toString(),
        workItemId = workItem.apiId.value,
        key = workItem.key,
        statusId = workItem.statusApiId.value,
        statusGroup = workItem.statusGroup.dbValue,
      )
    val notificationRecord =
      NotificationRecord(
        id = UUID.randomUUID(),
        apiId = PublicId.new("ntf"),
        recipientUserId = assigneeId,
        tenantId = tenantId,
        projectId = projectId,
        workItemId = workItem.id,
        sourceEventId = "evt_1",
        notificationType = "work_item.updated",
        title = "title",
        body = "body",
        payload = JsonObject(emptyMap()),
        readAt = null,
        createdAt = now,
      )

    "handle creates notification when assignee exists and claim succeeds" {
      coEvery {
        workItems.findByApiId(tenantId, projectId, workItem.apiId.value)
      } returns workItem
      coEvery { notifications.create(any()) } returns notificationRecord
      every { processed.tryClaim(WorkItemNotificationHandler.CONSUMER_NAME, "evt_1") } returns true

      runBlocking { handler.handle("evt_1", "work_item.updated", payload) }

      coVerify {
        notifications.create(
          match<CreateNotificationCommand> {
            it.recipientUserId == assigneeId && it.sourceEventId == "evt_1"
          }
        )
      }
    }

    "handle skips when tenant id is invalid" {
      runBlocking {
        handler.handle(
          "evt_2",
          "work_item.updated",
          payload.copy(tenantId = "not-a-uuid"),
        )
      }

      coVerify(exactly = 0) { notifications.create(any()) }
    }

    "handle skips when work item has no assignee" {
      coEvery {
        workItems.findByApiId(tenantId, projectId, workItem.apiId.value)
      } returns workItem.copy(assigneeId = null)

      runBlocking { handler.handle("evt_3", "work_item.updated", payload) }

      coVerify(exactly = 0) { notifications.create(any()) }
    }

    "handle still creates notification when claim fails after create" {
      coEvery {
        workItems.findByApiId(tenantId, projectId, workItem.apiId.value)
      } returns workItem
      coEvery { notifications.create(any()) } returns notificationRecord
      every { processed.tryClaim(WorkItemNotificationHandler.CONSUMER_NAME, "evt_4") } returns false

      runBlocking { handler.handle("evt_4", "work_item.updated", payload) }

      coVerify(exactly = 1) { notifications.create(any()) }
    }
  })
