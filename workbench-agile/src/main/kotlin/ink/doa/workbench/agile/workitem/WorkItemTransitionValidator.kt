package ink.doa.workbench.agile.workitem

import ink.doa.workbench.agile.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.agile.workitem.model.WorkItemRecord
import ink.doa.workbench.agile.workitem.model.WorkflowTransitionRecord
import ink.doa.workbench.kernel.common.errors.PermissionDeniedException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import java.util.UUID
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.springframework.stereotype.Component

data class WorkItemTransitionEvaluationRequest(
  val issue: WorkItemRecord,
  val actorUserId: UUID,
  val actorUserApiId: String,
  val projectApiId: String,
  val properties: Map<String, JsonElement>,
  val issueTypeConfigId: UUID,
)

@Component
class WorkItemTransitionValidator(
  private val repository: WorkItemRepository,
  private val accessPolicy: WorkItemAccessPolicyEngine,
) {
  private val conditions = WorkItemConditionEvaluator()

  suspend fun conditionContext(
    issue: WorkItemRecord,
    actorUserApiId: String,
    projectApiId: String,
    properties: Map<String, JsonElement>,
  ): WorkItemConditionContext =
    WorkItemConditionContext(
      workItem = issue,
      actorUserApiId = actorUserApiId,
      projectApiId = projectApiId,
      properties = properties,
      childIssuesNotDone =
        repository.countChildrenNotInStatusGroups(issue.tenantId, issue.id, setOf("done")),
    )

  suspend fun accessEvaluationContext(
    request: WorkItemTransitionEvaluationRequest
  ): ink.doa.workbench.agile.workitem.access.WorkItemAccessEvaluationContext {
    val conditionContext =
      conditionContext(
        request.issue,
        request.actorUserApiId,
        request.projectApiId,
        request.properties,
      )
    return ink.doa.workbench.agile.workitem.access.WorkItemAccessEvaluationContext(
      actor =
        accessPolicy.resolveActor(
          tenantId = request.issue.tenantId,
          projectId = request.issue.projectId,
          actorUserId = request.actorUserId,
        ),
      workItem = request.issue,
      issueTypeConfigId = request.issueTypeConfigId,
      projectApiId = request.projectApiId,
      properties = request.properties,
      childIssuesNotDone = conditionContext.childIssuesNotDone,
    )
  }

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
      throw ink.doa.workbench.kernel.common.errors.InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_TRANSITION_STATUS_MISMATCH
      )
    }
    if (config.statuses.none { it.statusId == transition.toStatusId }) {
      throw ink.doa.workbench.kernel.common.errors.InvalidRequestException(
        WorkbenchErrorCode.WORKFLOW_TRANSITION_STATUS_UNAVAILABLE
      )
    }
  }

  suspend fun requireTransitionPermission(
    issueTypeConfigId: UUID,
    transition: WorkflowTransitionRecord,
    evaluationContext: ink.doa.workbench.agile.workitem.access.WorkItemAccessEvaluationContext,
  ) {
    if (
      !accessPolicy.isTransitionPermitted(
        issueTypeConfigId = issueTypeConfigId,
        transitionId = transition.id,
        evaluationContext = evaluationContext,
      )
    ) {
      throw PermissionDeniedException(WorkbenchErrorCode.WORK_ITEM_TRANSITION_PERMISSION_DENIED)
    }
  }

  fun requireTransitionPrecondition(
    transition: WorkflowTransitionRecord,
    context: WorkItemConditionContext,
  ) {
    if (!conditions.evaluate(transition.preconditionAst, context)) {
      throw ink.doa.workbench.kernel.common.errors.InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_TRANSITION_PRECONDITION_FAILED
      )
    }
  }
}

data class TransitionConditionCheck(val enabled: Boolean, val reason: String?)
