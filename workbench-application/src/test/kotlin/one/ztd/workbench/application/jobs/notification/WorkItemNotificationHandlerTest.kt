package one.ztd.workbench.application.jobs.notification

import io.kotest.core.spec.style.StringSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.runBlocking
import one.ztd.workbench.agile.workitem.WorkItemRepository
import one.ztd.workbench.agile.workitem.events.WorkItemMutationEvent
import one.ztd.workbench.agile.workitem.model.WorkItemRecord
import one.ztd.workbench.agile.workitem.model.WorkItemStatusGroup
import one.ztd.workbench.notification.NotificationApplicationService
import one.ztd.workbench.notification.NotificationRecord

class WorkItemNotificationHandlerTest :
  StringSpec({
    val workItems = mockk<WorkItemRepository>()
    val notifications = mockk<NotificationApplicationService>()
    val handler = WorkItemNotificationHandler(workItems, notifications)
    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()
    val workItemId = UUID.randomUUID()
    val assigneeId = UUID.randomUUID()
    val eventId = "evt_1"
    val payload =
      WorkItemMutationEvent(
        tenantId = tenantId.toString(),
        projectId = projectId.toString(),
        workItemId = "wki_1",
        key = "CORE-1",
        statusId = "sts_1",
        statusGroup = WorkItemStatusGroup.IN_PROGRESS.dbValue,
      )

    fun workItem(assignee: UUID?): WorkItemRecord {
      val item = mockk<WorkItemRecord>()
      every { item.id } returns workItemId
      every { item.assigneeId } returns assignee
      return item
    }

    "duplicate event does not create a notification" {
      coEvery { workItems.findByApiId(tenantId, projectId, "wki_1") } returns workItem(assigneeId)
      coEvery { notifications.processWorkItemEvent(any(), any(), any()) } returns null

      runBlocking { handler.handle(eventId, "work_item.updated", payload) }

      coVerify(exactly = 1) {
        notifications.processWorkItemEvent("work-item-notifications", eventId, any())
      }
    }

    "event without assignee is claimed without a notification" {
      coEvery { workItems.findByApiId(tenantId, projectId, "wki_1") } returns workItem(null)
      coEvery {
        notifications.processWorkItemEvent("work-item-notifications", eventId, null)
      } returns null

      runBlocking { handler.handle(eventId, "work_item.updated", payload) }

      coVerify { notifications.processWorkItemEvent("work-item-notifications", eventId, null) }
    }

    "invalid tenant or project identifiers are ignored" {
      clearMocks(workItems, notifications)
      val invalidPayload = payload.copy(tenantId = "not-a-uuid")
      runBlocking { handler.handle(eventId, "work_item.updated", invalidPayload) }
      coVerify(exactly = 0) { workItems.findByApiId(any(), any(), any()) }
      coVerify(exactly = 0) { notifications.processWorkItemEvent(any(), any(), any()) }
    }

    "missing work item is ignored" {
      clearMocks(workItems, notifications)
      coEvery { workItems.findByApiId(tenantId, projectId, "wki_1") } returns null
      runBlocking { handler.handle(eventId, "work_item.updated", payload) }
      coVerify(exactly = 1) { workItems.findByApiId(tenantId, projectId, "wki_1") }
      coVerify(exactly = 0) { notifications.processWorkItemEvent(any(), any(), any()) }
    }

    "first delivery creates a notification with event details" {
      clearMocks(workItems, notifications)
      coEvery { workItems.findByApiId(tenantId, projectId, "wki_1") } returns workItem(assigneeId)
      coEvery { notifications.processWorkItemEvent(any(), any(), any()) } returns
        mockk<NotificationRecord>()

      runBlocking { handler.handle(eventId, "work_item.updated", payload) }

      coVerify {
        notifications.processWorkItemEvent(
          "work-item-notifications",
          eventId,
          match {
            it.recipientUserId == assigneeId &&
              it.notificationType == "work_item.updated" &&
              it.title.contains("CORE-1")
          },
        )
      }
    }
  })
