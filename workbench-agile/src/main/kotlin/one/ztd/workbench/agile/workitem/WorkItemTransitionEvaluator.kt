package one.ztd.workbench.agile.workitem

import one.ztd.workbench.agile.workitem.model.WorkflowTransitionRecord
import one.ztd.workbench.agile.workitem.template.TransitionFieldsParser
import one.ztd.workbench.agile.workitem.template.WorkItemTransitionFieldsTemplate
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.PermissionDeniedException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import org.springframework.stereotype.Component

data class TransitionEvaluation(
  val applicable: Boolean,
  val permitted: Boolean,
  val preconditionMet: Boolean,
  val fieldsValid: Boolean,
  val fieldsTemplate: WorkItemTransitionFieldsTemplate?,
  val disableReason: String?,
) {
  val enabled: Boolean = applicable && permitted && preconditionMet && fieldsValid

  fun failureOrNull(): TransitionFailure? =
    when {
      !applicable -> TransitionFailure(WorkbenchErrorCode.WORK_ITEM_TRANSITION_STATUS_MISMATCH)
      !permitted -> TransitionFailure(WorkbenchErrorCode.WORK_ITEM_TRANSITION_PERMISSION_DENIED)
      !preconditionMet ->
        TransitionFailure(WorkbenchErrorCode.WORK_ITEM_TRANSITION_PRECONDITION_FAILED)
      !fieldsValid -> TransitionFailure(WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELDS_INVALID_JSON)
      else -> null
    }
}

data class TransitionFailure(val errorCode: WorkbenchErrorCode)

@Component
class WorkItemTransitionEvaluator(
  private val transitionValidator: WorkItemTransitionValidator,
  private val accessPolicy: WorkItemAccessPolicyEngine,
  private val transitionFieldsParser: TransitionFieldsParser,
) {
  suspend fun evaluate(
    context: WorkItemTransitionContext,
    transition: WorkflowTransitionRecord,
  ): TransitionEvaluation {
    val applicable = isApplicable(context, transition)
    val targetStatusAvailable =
      transition.toStatusId in context.config.statuses.map { it.statusId }.toSet()
    val accessEvaluation = context.accessEvaluation
    val permitted =
      if (applicable && targetStatusAvailable) {
        accessPolicy.isTransitionPermitted(
          issueTypeConfigId = context.config.config.id,
          transitionId = transition.id,
          evaluationContext = accessEvaluation,
          preloadedRules = context.permissionContext.accessRules,
        )
      } else {
        false
      }
    val precondition =
      transitionValidator.checkCondition(
        transition.preconditionAst,
        context.conditionContext,
        failedReason = "Transition precondition is not satisfied.",
        invalidReason = "Transition precondition is invalid.",
      )
    val fieldsTemplate = runCatching { transitionFieldsParser.parse(transition.fields) }
    return TransitionEvaluation(
      applicable = applicable && targetStatusAvailable,
      permitted = permitted,
      preconditionMet = precondition.enabled,
      fieldsValid = fieldsTemplate.isSuccess,
      fieldsTemplate = fieldsTemplate.getOrNull(),
      disableReason =
        when {
          !applicable -> "Transition is not available from the work item's current status."
          !targetStatusAvailable -> "Transition target status is not available in this type config."
          !permitted -> "Transition permission condition is not satisfied."
          precondition.reason != null -> precondition.reason
          fieldsTemplate.isFailure -> "Transition field requirements are invalid."
          else -> null
        },
    )
  }

  suspend fun evaluateOrThrow(
    context: WorkItemTransitionContext,
    transition: WorkflowTransitionRecord,
  ): TransitionEvaluation {
    transitionValidator.requireTransitionApplicable(context.issue, context.config, transition)
    val evaluation = evaluate(context, transition)
    throwEvaluationFailure(evaluation, transition)
    return evaluation
  }

  private fun throwEvaluationFailure(
    evaluation: TransitionEvaluation,
    transition: WorkflowTransitionRecord,
  ) {
    val failure = evaluation.failureOrNull() ?: return
    if (failure.errorCode == WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELDS_INVALID_JSON) {
      transitionFieldsParser.parse(transition.fields)
    }
    val exception =
      when (failure.errorCode) {
        WorkbenchErrorCode.WORK_ITEM_TRANSITION_PERMISSION_DENIED ->
          PermissionDeniedException(failure.errorCode)
        else -> InvalidRequestException(failure.errorCode)
      }
    throw exception
  }

  private fun isApplicable(
    context: WorkItemTransitionContext,
    transition: WorkflowTransitionRecord,
  ): Boolean =
    transition.workflowId == context.config.config.workflowId &&
      (transition.fromStatusId == null || transition.fromStatusId == context.issue.statusId)
}
