package ink.doa.workbench.worker.notification

import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.events.WorkItemMutationEvent
import ink.doa.workbench.data.messaging.ProcessedDomainEventRepository
import ink.doa.workbench.service.notification.NotificationApplicationService
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class WorkItemNotificationHandler(
  private val workItems: WorkItemRepository,
  private val notifications: NotificationApplicationService,
  private val processed: ProcessedDomainEventRepository,
) {
  private val logger = LoggerFactory.getLogger(javaClass)

  suspend fun handle(eventId: String, type: String, payload: WorkItemMutationEvent) {
    val tenantId = payload.tenantId.toUuidOrNull() ?: return
    val projectId = payload.projectId.toUuidOrNull() ?: return
    val workItem = workItems.findByApiId(tenantId, projectId, payload.workItemId) ?: return
    val assignee = workItem.assigneeId ?: return
    notifications.create(
      ink.doa.workbench.core.notification.CreateNotificationCommand(
        recipientUserId = assignee,
        tenantId = tenantId,
        projectId = projectId,
        workItemId = workItem.id,
        sourceEventId = eventId,
        notificationType = type,
        title = "工作项 ${payload.key} 有更新",
        body = "工作项状态为 ${payload.statusGroup}",
        payload =
          buildJsonObject {
            put("eventType", type)
            put("workItemId", payload.workItemId)
            put("key", payload.key)
          },
      )
    )
    if (!processed.tryClaim(CONSUMER_NAME, eventId)) {
      logger.info(
        "work_item_notification_duplicate eventId={} workItemId={}",
        eventId,
        payload.workItemId,
      )
      return
    }
    logger.info(
      "work_item_notification_created eventId={} workItemId={} recipient={}",
      eventId,
      payload.workItemId,
      assignee,
    )
  }

  private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()

  companion object {
    const val CONSUMER_NAME = "work-item-notifications"
  }
}
