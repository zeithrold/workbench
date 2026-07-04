package ink.doa.workbench.core.workitem.template

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.IssueTypeConfigPropertyRecord
import ink.doa.workbench.core.workitem.model.WorkItemPropertyDataType
import ink.doa.workbench.core.workitem.model.WorkItemPropertyValueValidator

object WorkItemValueTemplateValidator {
  private val writableSystemFields = setOf("title", "description", "assignee", "priority", "sprint")
  private val supportedVariables =
    setOf(
      "user.currentUser",
      "project.currentProject",
      "date.now",
      "date.today",
      "date.startOfWeek",
      "date.endOfWeek",
    )
  private val dateAnchors = setOf("date.now", "date.today", "date.startOfWeek", "date.endOfWeek")

  @Suppress("ThrowsCount")
  fun validateEnvelope(template: WorkItemValueTemplate) {
    if (template.version != WorkItemValueTemplate.CURRENT_VERSION) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_TEMPLATE_VERSION_UNSUPPORTED,
        "Unsupported work item value template version: ${template.version}",
      )
    }
    if (template.resource != WorkItemValueTemplate.RESOURCE) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_TEMPLATE_RESOURCE_UNSUPPORTED,
        "Unsupported work item value template resource: ${template.resource}",
      )
    }
    if (template.values.size > MAX_VALUES) {
      throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_TEMPLATE_TOO_MANY_VALUES)
    }
  }

  fun validate(template: WorkItemValueTemplate, config: IssueTypeConfigDetails) {
    validateEnvelope(template)
    template.values.forEach { (field, expression) ->
      val property = validateWritableField(field, config)
      validateExpression(expression, config, property)
    }
  }

  fun validateExpression(
    expression: TemplateValueExpression,
    config: IssueTypeConfigDetails,
    targetProperty: IssueTypeConfigPropertyRecord?,
  ) {
    when (expression) {
      is TemplateValueExpression.Copy -> validateCopyField(expression.field, config)
      is TemplateValueExpression.Variable -> validateVariable(expression.name)
      is TemplateValueExpression.RelativeDate -> validateRelativeDate(expression, targetProperty)
      is TemplateValueExpression.Clear -> Unit
      is TemplateValueExpression.Literal ->
        targetProperty?.let { WorkItemPropertyValueValidator.validate(it, expression.value) }
    }
  }

  fun validateWritableField(
    field: TemplateField,
    config: IssueTypeConfigDetails,
  ): IssueTypeConfigPropertyRecord? =
    when (field) {
      is TemplateField.System -> {
        if (field.canonicalName !in writableSystemFields) {
          throw InvalidRequestException(
            WorkbenchErrorCode.WORK_ITEM_TEMPLATE_FIELD_NOT_WRITABLE,
            "Field is not writable by work item value templates: ${field.canonicalName}",
          )
        }
        null
      }
      is TemplateField.Property -> resolveProperty(field, config)
    }

  fun resolveProperty(
    field: TemplateField.Property,
    config: IssueTypeConfigDetails,
  ): IssueTypeConfigPropertyRecord =
    config.properties.singleOrNull {
      it.propertyApiId.value == field.apiId || it.code == field.code
    }
      ?: throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_PROPERTY_UNAVAILABLE,
        "Property is not available in this config: ${field.canonicalName}",
      )

  private fun validateCopyField(field: TemplateField, config: IssueTypeConfigDetails) {
    when (field) {
      is TemplateField.System -> Unit
      is TemplateField.Property -> resolveProperty(field, config)
    }
  }

  private fun validateVariable(name: String) {
    if (name in supportedVariables) return
    if (name.startsWith("workItem.current.") || name.startsWith("workItem.previous.")) return
    throw InvalidRequestException(
      WorkbenchErrorCode.WORK_ITEM_TEMPLATE_VARIABLE_UNKNOWN,
      "Unknown work item value template variable: $name",
    )
  }

  @Suppress("ThrowsCount")
  private fun validateRelativeDate(
    expression: TemplateValueExpression.RelativeDate,
    targetProperty: IssueTypeConfigPropertyRecord?,
  ) {
    if (expression.amount <= 0) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_TEMPLATE_RELATIVE_DATE_AMOUNT_POSITIVE
      )
    }
    if (expression.anchor !in dateAnchors) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_TEMPLATE_RELATIVE_DATE_ANCHOR_UNKNOWN,
        "Unknown relative date anchor: ${expression.anchor}",
      )
    }
    if (
      targetProperty != null &&
        targetProperty.dataType !in
          setOf(WorkItemPropertyDataType.DATE, WorkItemPropertyDataType.DATETIME)
    ) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_TEMPLATE_VALUE_TYPE_INVALID,
        "Relative date template requires a date-like property: ${targetProperty.code}",
      )
    }
  }

  private const val MAX_VALUES = 64
}
