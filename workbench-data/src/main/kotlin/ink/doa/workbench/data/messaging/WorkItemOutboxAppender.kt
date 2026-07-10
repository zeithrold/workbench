package ink.doa.workbench.data.messaging

import ink.doa.workbench.core.messaging.EventMetadata
import ink.doa.workbench.core.port.messaging.DomainEventOutbox
import ink.doa.workbench.core.workitem.ReassignSprintBatchCommand
import ink.doa.workbench.core.workitem.events.WorkItemDomainEvents
import ink.doa.workbench.core.workitem.events.WorkItemMutationEvent
import ink.doa.workbench.core.workitem.events.WorkItemSprintChangedEvent
import ink.doa.workbench.core.workitem.events.WorkItemSprintDomainEvents
import ink.doa.workbench.core.workitem.model.WorkItemMutationResult
import ink.doa.workbench.core.workitem.model.WorkItemRecord
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
