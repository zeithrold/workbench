package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.WorkItemCommentFormMeta
import ink.doa.workbench.core.workitem.model.WorkItemFormFieldMeta
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemTransitionOption
import ink.doa.workbench.core.workitem.model.WorkflowTransitionRecord
import ink.doa.workbench.core.workitem.template.TransitionFieldsParser
import ink.doa.workbench.core.workitem.template.WorkItemTransitionFieldsTemplate
import ink.doa.workbench.core.workitem.template.WorkItemValueTemplateContext
import java.util.UUID
import kotlinx.serialization.json.JsonElement
import org.springframework.stereotype.Component

data class TransitionOptionBuildContext(
  val issue: WorkItemRecord,
  val config: IssueTypeConfigDetails,
  val tenantId: UUID,
  val projectId: UUID,
  val actorUserId: UUID,
  val currentProperties: Map<String, JsonElement>,
  val context: WorkItemConditionContext,
  val permissionContext: WorkItemFieldPermissionContext,
)

@Component
class WorkItemTransitionOptionBuilder(
  private val mutationSupport: WorkItemMutationSupport,
  private val fieldMutationEngine: WorkItemFieldMutationEngine,
  private val transitionValidator: WorkItemTransitionValidator,
) {
  private val transitionFieldsParser = TransitionFieldsParser()

  suspend fun build(
    transition: WorkflowTransitionRecord,
    buildContext: TransitionOptionBuildContext,
  ): WorkItemTransitionOption {
    val fieldsTemplate = runCatching { transitionFieldsParser.parse(transition.fields) }
    val templateContext =
      mutationSupport.templateContext(
        WorkItemTemplateContextRequest(
          tenantId = buildContext.tenantId,
          projectId = buildContext.projectId,
          actorUserId = buildContext.actorUserId,
          workItem = buildContext.issue,
          currentProperties = buildContext.currentProperties,
        )
      )
    val availability = transitionAvailability(transition, buildContext, fieldsTemplate)
    val formDetails =
      transitionFormDetails(
        fieldsTemplate = fieldsTemplate.getOrNull(),
        config = buildContext.config,
        templateContext = templateContext,
        permissionContext = buildContext.permissionContext,
      )
    return WorkItemTransitionOption(
      id = transition.apiId,
      name = transition.name,
      fromStatusId = transition.fromStatusApiId,
      toStatusId =
        transition.toStatusApiId
          ?: throw ResourceNotFoundException(
            WorkbenchErrorCode.RESOURCE_WORK_ITEM_STATUS_NOT_FOUND
          ),
      enabled = availability.enabled,
      reason = availability.reason,
      fields = transition.fields,
      editableFields = formDetails.editableFields,
      fieldMeta = formDetails.fieldMeta,
      commentMeta = formDetails.commentMeta,
    )
  }

  private fun transitionAvailability(
    transition: WorkflowTransitionRecord,
    buildContext: TransitionOptionBuildContext,
    fieldsTemplate: Result<WorkItemTransitionFieldsTemplate>,
  ): TransitionAvailability {
    val targetStatusAvailable =
      transition.toStatusId in buildContext.config.statuses.map { it.statusId }.toSet()
    val permissionCondition =
      transitionValidator.checkCondition(
        transition.permissionCondition,
        buildContext.context,
        failedReason = "Transition permission condition is not satisfied.",
        invalidReason = "Transition permission condition is invalid.",
      )
    val precondition =
      transitionValidator.checkCondition(
        transition.preconditionAst,
        buildContext.context,
        failedReason = "Transition precondition is not satisfied.",
        invalidReason = "Transition precondition is invalid.",
      )
    val enabled =
      targetStatusAvailable &&
        permissionCondition.enabled &&
        precondition.enabled &&
        fieldsTemplate.isSuccess
    val reason =
      when {
        !targetStatusAvailable -> "Transition target status is not available in this type config."
        permissionCondition.reason != null -> permissionCondition.reason
        precondition.reason != null -> precondition.reason
        fieldsTemplate.isFailure -> "Transition field requirements are invalid."
        else -> null
      }
    return TransitionAvailability(enabled = enabled, reason = reason)
  }

  private suspend fun transitionFormDetails(
    fieldsTemplate: WorkItemTransitionFieldsTemplate?,
    config: IssueTypeConfigDetails,
    templateContext: WorkItemValueTemplateContext,
    permissionContext: WorkItemFieldPermissionContext,
  ): TransitionFormDetails {
    val formPlan = fieldsTemplate?.let {
      fieldMutationEngine.planForm(
        template = it,
        config = config,
        templateContext = templateContext,
        permissionContext = permissionContext,
      )
    }
    val commentMeta = fieldsTemplate?.let {
      fieldMutationEngine.buildCommentMeta(it.comment, templateContext)
    }
    return TransitionFormDetails(
      editableFields = formPlan?.editableWirePaths.orEmpty(),
      fieldMeta = formPlan?.fieldMeta.orEmpty(),
      commentMeta = commentMeta,
    )
  }

  private data class TransitionAvailability(val enabled: Boolean, val reason: String?)

  private data class TransitionFormDetails(
    val editableFields: List<String>,
    val fieldMeta: List<WorkItemFormFieldMeta>,
    val commentMeta: WorkItemCommentFormMeta?,
  )
}
