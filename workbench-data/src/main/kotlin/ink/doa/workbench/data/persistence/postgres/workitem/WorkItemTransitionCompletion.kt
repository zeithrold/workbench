package ink.doa.workbench.data.persistence.postgres.workitem

import ink.doa.workbench.core.workitem.model.TransitionWorkItemCommand
import ink.doa.workbench.core.workitem.model.WorkItemMutationResult
import ink.doa.workbench.core.workitem.model.WorkItemPropertyValue
import java.time.OffsetDateTime
import java.util.UUID

internal data class WorkItemTransitionCompletion(
  val command: TransitionWorkItemCommand,
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
  completion: WorkItemTransitionCompletion
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
  return WorkItemMutationResult(
    workItem = requireWorkItem(command.tenantId, command.projectId, command.workItemApiId),
    eventType = "work_item.transitioned",
    statusHistoryId = statusHistoryId,
  )
}
