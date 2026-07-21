package one.ztd.workbench.agile.workitem

import one.ztd.workbench.agile.workitem.model.WorkItemCommentFormMeta
import one.ztd.workbench.agile.workitem.model.WorkItemFormFieldMeta
import one.ztd.workbench.agile.workitem.model.WorkItemTransitionOption
import one.ztd.workbench.agile.workitem.model.WorkflowTransitionRecord
import one.ztd.workbench.agile.workitem.template.WorkItemTransitionFieldsTemplate
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import org.springframework.stereotype.Component

@Component
class WorkItemTransitionOptionBuilder(private val fieldPipeline: WorkItemFieldMutationPipeline) {
  suspend fun build(
    transition: WorkflowTransitionRecord,
    context: WorkItemTransitionContext,
    evaluation: TransitionEvaluation,
  ): WorkItemTransitionOption {
    val formDetails = transitionFormDetails(evaluation.fieldsTemplate, context)
    return WorkItemTransitionOption(
      id = transition.apiId,
      name = transition.name,
      fromStatusId = transition.fromStatusApiId,
      toStatusId =
        transition.toStatusApiId
          ?: throw ResourceNotFoundException(
            WorkbenchErrorCode.RESOURCE_WORK_ITEM_STATUS_NOT_FOUND
          ),
      enabled = evaluation.enabled,
      reason = evaluation.disableReason,
      fields = transition.fields,
      editableFields = formDetails.editableFields,
      fieldMeta = formDetails.fieldMeta,
      commentMeta = formDetails.commentMeta,
      targetStatus =
        context.config.statuses
          .singleOrNull { it.statusId == transition.toStatusId }
          ?.let {
            one.ztd.workbench.agile.workitem.model.WorkItemStatusSummary(
              id = it.statusApiId.value,
              code = it.code,
              name = it.name,
              group = it.statusGroup.dbValue,
              color = null,
              terminal = it.isTerminal,
            )
          },
    )
  }

  private suspend fun transitionFormDetails(
    fieldsTemplate: WorkItemTransitionFieldsTemplate?,
    context: WorkItemTransitionContext,
  ): TransitionFormDetails {
    val formPlan = fieldsTemplate?.let {
      fieldPipeline.engine.planForm(
        template = it,
        config = context.config,
        templateContext = context.templateContext,
        permissionContext = context.permissionContext,
      )
    }
    val commentMeta = fieldsTemplate?.let {
      fieldPipeline.engine.buildCommentMeta(it.comment, context.templateContext)
    }
    return TransitionFormDetails(
      editableFields = formPlan?.editableWirePaths.orEmpty(),
      fieldMeta = formPlan?.fieldMeta.orEmpty(),
      commentMeta = commentMeta,
    )
  }

  private data class TransitionFormDetails(
    val editableFields: List<String>,
    val fieldMeta: List<WorkItemFormFieldMeta>,
    val commentMeta: WorkItemCommentFormMeta?,
  )
}
