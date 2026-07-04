package ink.doa.workbench.core.workitem.template

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails

object TransitionFieldsValidator {
  private const val MAX_FIELDS = 64

  @Suppress("ThrowsCount")
  fun validateEnvelope(
    template: WorkItemTransitionFieldsTemplate,
    expectedTarget: WorkItemValueTemplateTarget = WorkItemValueTemplateTarget.TRANSITION,
  ) {
    if (template.version != WorkItemTransitionFieldsTemplate.CURRENT_VERSION) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELDS_VERSION_UNSUPPORTED,
        "Unsupported transition fields version: ${template.version}",
      )
    }
    if (template.resource != WorkItemTransitionFieldsTemplate.RESOURCE) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELDS_RESOURCE_UNSUPPORTED,
        "Unsupported transition fields resource: ${template.resource}",
      )
    }
    if (template.target != expectedTarget) {
      throw InvalidRequestException(
        targetErrorCode(expectedTarget),
        "Fields template target must be ${expectedTarget.wireName}.",
      )
    }
    if (template.fields.isEmpty() && expectedTarget == WorkItemValueTemplateTarget.CREATE) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_CREATE_FIELDS_REQUIRED,
        "Create fields template must define at least one field.",
      )
    }
    if (template.fields.size > MAX_FIELDS) {
      throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELDS_TOO_MANY)
    }
  }

  fun validate(
    template: WorkItemTransitionFieldsTemplate,
    config: IssueTypeConfigDetails,
    expectedTarget: WorkItemValueTemplateTarget = WorkItemValueTemplateTarget.TRANSITION,
  ) {
    validateEnvelope(template, expectedTarget)
    template.fields.forEach { (field, spec) ->
      val property = WorkItemValueTemplateValidator.validateWritableField(field, config)
      spec.value?.let {
        WorkItemValueTemplateValidator.validateExpression(it, config, property)
      }
      if (spec.participation == FieldParticipation.AUTOMATIC && spec.value == null) {
        throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELDS_AUTOMATIC_VALUE_REQUIRED,
          "Automatic field requires a value expression: ${field.toWirePath()}",
        )
      }
    }
  }

  private fun targetErrorCode(expectedTarget: WorkItemValueTemplateTarget): WorkbenchErrorCode =
    when (expectedTarget) {
      WorkItemValueTemplateTarget.CREATE ->
        WorkbenchErrorCode.WORK_ITEM_CREATE_FIELDS_TARGET_INVALID
      WorkItemValueTemplateTarget.TRANSITION ->
        WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELDS_TARGET_INVALID
    }
}
