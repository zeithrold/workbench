package ink.doa.workbench.agile.workitem.events

import ink.doa.workbench.kernel.messaging.DomainEventSpec
import ink.doa.workbench.kernel.messaging.DomainTopics
import kotlinx.serialization.Serializable

@Serializable
data class WorkItemSprintChangedEvent(
  val tenantId: String,
  val projectId: String,
  val workItemId: String,
  val sourceSprintId: String,
  val targetSprintId: String?,
  val disposition: String,
  val operationId: String,
  val actorUserId: String,
)

object WorkItemSprintDomainEvents {
  val SprintChanged =
    DomainEventSpec(
      type = "work_item.sprint_changed",
      topic = DomainTopics.WORK_ITEM,
      serializer = WorkItemSprintChangedEvent.serializer(),
    )
}
