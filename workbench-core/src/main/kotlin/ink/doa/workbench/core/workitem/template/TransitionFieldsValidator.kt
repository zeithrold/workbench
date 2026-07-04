package ink.doa.workbench.core.workitem.template

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails

object TransitionFieldsValidator {
  private const val MAX_FIELDS = 64

  @Suppress("ThrowsCount")
  fun validateEnvelope(template: WorkItemTransitionFieldsTemplate) {
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
    if (template.target != WorkItemValueTemplateTarget.TRANSITION) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELDS_TARGET_INVALID,
        "Transition fields target must be transition.",
      )
    }
    if (template.fields.size > MAX_FIELDS) {
      throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELDS_TOO_MANY)
    }
  }

  fun validate(template: WorkItemTransitionFieldsTemplate, config: IssueTypeConfigDetails) {
    validateEnvelope(template)
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
}
