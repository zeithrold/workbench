package one.ztd.workbench.data.messaging

import one.ztd.workbench.agile.workitem.ReassignSprintBatchCommand
import one.ztd.workbench.agile.workitem.events.WorkItemDomainEvents
import one.ztd.workbench.agile.workitem.events.WorkItemMutationEvent
import one.ztd.workbench.agile.workitem.events.WorkItemSprintChangedEvent
import one.ztd.workbench.agile.workitem.events.WorkItemSprintDomainEvents
import one.ztd.workbench.agile.workitem.model.WorkItemMutationResult
import one.ztd.workbench.agile.workitem.model.WorkItemRecord
import one.ztd.workbench.application.messaging.DomainEventOutbox
import one.ztd.workbench.kernel.messaging.EventMetadata
import org.springframework.stereotype.Component

@Component
class WorkItemOutboxAppender(private val outbox: DomainEventOutbox) {
  fun append(result: WorkItemMutationResult) {
    val spec =
      when (result.eventType) {
        WorkItemDomainEvents.Created.type -> WorkItemDomainEvents.Created
        WorkItemDomainEvents.Updated.type -> WorkItemDomainEvents.Updated
        WorkItemDomainEvents.Transitioned.type -> WorkItemDomainEvents.Transitioned
        else -> return
      }
    outbox.append(
      spec = spec,
      key = result.workItem.apiId.value,
      payload = WorkItemMutationEvent.from(result.workItem),
      metadata = EventMetadata(tenantId = result.workItem.tenantId.toString()),
    )
  }

  fun appendSprintChanged(
    workItem: WorkItemRecord,
    command: ReassignSprintBatchCommand,
    sourceSprintApiId: String,
    targetSprintApiId: String?,
  ) {
    outbox.append(
      spec = WorkItemSprintDomainEvents.SprintChanged,
      key = workItem.apiId.value,
      payload =
        WorkItemSprintChangedEvent(
          tenantId = command.tenantId.toString(),
          projectId = command.projectId.toString(),
          workItemId = workItem.apiId.value,
          sourceSprintId = sourceSprintApiId,
          targetSprintId = targetSprintApiId,
          disposition = command.disposition.name,
          operationId = command.operationId,
          actorUserId = command.actorUserId.toString(),
        ),
      metadata = EventMetadata(tenantId = command.tenantId.toString()),
    )
  }
}
