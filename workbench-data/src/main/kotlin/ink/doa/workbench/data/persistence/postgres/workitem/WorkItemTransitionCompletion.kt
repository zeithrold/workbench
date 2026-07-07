package ink.doa.workbench.data.persistence.postgres.workitem

import ink.doa.workbench.core.workitem.model.TransitionPersistenceCommand
import ink.doa.workbench.core.workitem.model.WorkItemMutationResult
import ink.doa.workbench.core.workitem.model.WorkItemPropertyValue
import ink.doa.workbench.core.workitem.stream.WorkItemEventCodec
import ink.doa.workbench.data.repository.workitem.WorkItemActivityContext
import ink.doa.workbench.data.repository.workitem.WorkItemEventFactory
import ink.doa.workbench.data.repository.workitem.WorkItemStatusChangedInput
import java.time.OffsetDateTime
import java.util.UUID

internal data class WorkItemTransitionCompletion(
  val command: TransitionPersistenceCommand,
  val issueId: UUID,
  val fromStatusId: UUID,
  val toStatusId: UUID,
  val transitionId: UUID,
  val propertyValues: List<WorkItemPropertyValue>,
  val previousSprintId: UUID?,
  val nextSprintId: UUID?,
  val now: OffsetDateTime,
)

internal fun completeWorkItemTransition(
  completion: WorkItemTransitionCompletion,
  eventFactory: WorkItemEventFactory,
  eventCodec: WorkItemEventCodec,
): WorkItemMutationResult {
  val command = completion.command
  if (command.sprintApiId != null) {
    recordIssueSprintChange(
      IssueSprintChange(
        tenantId = command.tenantId,
        issueId = completion.issueId,
        previousSprintId = completion.previousSprintId,
        nextSprintId = completion.nextSprintId,
        actorUserId = command.actorUserId,
        changedAt = completion.now,
      )
    )
  }
  replacePropertyValues(
    command.tenantId,
    completion.issueId,
    completion.propertyValues,
    command.actorUserId,
    completion.now,
  )
  val statusHistoryId =
    insertStatusHistory(
      StatusHistoryEntry(
        tenantId = command.tenantId,
        issueId = completion.issueId,
        fromStatusId = completion.fromStatusId,
        toStatusId = completion.toStatusId,
        transitionId = completion.transitionId,
        actorUserId = command.actorUserId,
        changedAt = completion.now,
      )
    )
  val insertedEvent =
    appendWorkItemEvent(
      eventCodec,
      eventFactory.statusChanged(
        WorkItemStatusChangedInput(
          context =
            WorkItemActivityContext(
              tenantId = command.tenantId,
              projectId = command.projectId,
              workItemId = completion.issueId,
              actorUserId = command.actorUserId,
              occurredAt = completion.now,
            ),
          fromStatusId = completion.fromStatusId,
          toStatusId = completion.toStatusId,
          transitionId = completion.transitionId,
        )
      ),
    )
  return WorkItemMutationResult(
    workItem = requireWorkItem(command.tenantId, command.projectId, command.workItemApiId),
    eventType = "work_item.transitioned",
    statusHistoryId = statusHistoryId,
    streamEventId = insertedEvent.id,
    streamEventApiId = insertedEvent.apiId,
  )
}
