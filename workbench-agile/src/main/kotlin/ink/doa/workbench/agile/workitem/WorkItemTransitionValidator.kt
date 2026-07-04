package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.PermissionDeniedException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkflowTransitionRecord
import java.util.UUID
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.springframework.stereotype.Component

@Component
class WorkItemTransitionValidator(
  private val repository: WorkItemRepository,
) {
  private val conditions = WorkItemConditionEvaluator()

  suspend fun conditionContext(
    issue: WorkItemRecord,
    actorUserId: UUID,
    properties: Map<String, JsonElement>,
  ): WorkItemConditionContext =
    WorkItemConditionContext(
      workItem = issue,
      actorUserId = actorUserId,
      properties = properties,
      childIssuesNotDone =
        repository.countChildrenNotInStatusGroups(issue.tenantId, issue.id, setOf("done")),
    )

  fun checkCondition(
    ast: JsonObject,
    context: WorkItemConditionContext,
    failedReason: String,
    invalidReason: String,
  ): TransitionConditionCheck =
    runCatching { conditions.evaluate(ast, context) }
      .fold(
        onSuccess = { TransitionConditionCheck(it, if (it) null else failedReason) },
        onFailure = { TransitionConditionCheck(false, invalidReason) },
      )

  fun requireTransitionApplicable(
    issue: WorkItemRecord,
    config: IssueTypeConfigDetails,
    transition: WorkflowTransitionRecord,
  ) {
    if (
      transition.workflowId != config.config.workflowId ||
        (transition.fromStatusId != null && transition.fromStatusId != issue.statusId)
    ) {
      throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_TRANSITION_STATUS_MISMATCH)
    }
    if (config.statuses.none { it.statusId == transition.toStatusId }) {
      throw InvalidRequestException(WorkbenchErrorCode.WORKFLOW_TRANSITION_STATUS_UNAVAILABLE)
    }
  }

  fun requireTransitionPermission(
    transition: WorkflowTransitionRecord,
    context: WorkItemConditionContext,
  ) {
    if (!conditions.evaluate(transition.permissionCondition, context)) {
      throw PermissionDeniedException(WorkbenchErrorCode.WORK_ITEM_TRANSITION_PERMISSION_DENIED)
    }
  }

  fun requireTransitionPrecondition(
    transition: WorkflowTransitionRecord,
    context: WorkItemConditionContext,
  ) {
    if (!conditions.evaluate(transition.preconditionAst, context)) {
      throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_TRANSITION_PRECONDITION_FAILED)
    }
  }
}

data class TransitionConditionCheck(val enabled: Boolean, val reason: String?)
