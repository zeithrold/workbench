package one.ztd.workbench.agile.workitem.template

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import one.ztd.workbench.agile.workitem.model.IssueTypeConfigDetails
import one.ztd.workbench.agile.workitem.richtext.RichTextProcessor
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.kernel.common.errors.requireValid

object TransitionFieldsValidator {
  private const val MAX_FIELDS = 64

  fun validateEnvelope(
    template: WorkItemTransitionFieldsTemplate,
    expectedTarget: WorkItemValueTemplateTarget = WorkItemValueTemplateTarget.TRANSITION,
  ) {
    validateEnvelopeVersion(template)
    validateEnvelopeResource(template)
    validateEnvelopeTarget(template, expectedTarget)
    validateEnvelopeFieldCount(template, expectedTarget)
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
        if (field is TemplateField.System && field.canonicalName == "description") {
          validateDescriptionTemplateLiteral(it)
        }
      }
      requireValid(
        spec.participation != FieldParticipation.AUTOMATIC || spec.value != null,
        WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELDS_AUTOMATIC_VALUE_REQUIRED,
        "Automatic field requires a value expression: ${field.toWirePath()}",
      )
    }
    template.comment?.let { validateCommentSpec(it) }
  }

  private fun validateEnvelopeVersion(template: WorkItemTransitionFieldsTemplate) {
    requireValid(
      template.version == WorkItemTransitionFieldsTemplate.CURRENT_VERSION,
      WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELDS_VERSION_UNSUPPORTED,
      "Unsupported transition fields version: ${template.version}",
    )
  }

  private fun validateEnvelopeResource(template: WorkItemTransitionFieldsTemplate) {
    requireValid(
      template.resource == WorkItemTransitionFieldsTemplate.RESOURCE,
      WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELDS_RESOURCE_UNSUPPORTED,
      "Unsupported transition fields resource: ${template.resource}",
    )
  }

  private fun validateEnvelopeTarget(
    template: WorkItemTransitionFieldsTemplate,
    expectedTarget: WorkItemValueTemplateTarget,
  ) {
    requireValid(
      template.target == expectedTarget,
      targetErrorCode(expectedTarget),
      "Fields template target must be ${expectedTarget.wireName}.",
    )
  }

  private fun validateEnvelopeFieldCount(
    template: WorkItemTransitionFieldsTemplate,
    expectedTarget: WorkItemValueTemplateTarget,
  ) {
    requireValid(
      template.fields.isNotEmpty() || expectedTarget != WorkItemValueTemplateTarget.CREATE,
      WorkbenchErrorCode.WORK_ITEM_CREATE_FIELDS_REQUIRED,
      "Create fields template must define at least one field.",
    )
    requireValid(
      template.fields.size <= MAX_FIELDS,
      WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELDS_TOO_MANY,
    )
  }

  private fun validateCommentSpec(spec: CommentFieldSpec) {
    requireValid(
      spec.participation != FieldParticipation.AUTOMATIC,
      WorkbenchErrorCode.WORK_ITEM_TRANSITION_COMMENT_PARTICIPATION_INVALID,
      "Comment participation cannot be automatic.",
    )
    requireValid(
      spec.participation != FieldParticipation.REQUIRED || spec.template != null,
      WorkbenchErrorCode.WORK_ITEM_TRANSITION_COMMENT_TEMPLATE_REQUIRED,
      "Required transition comment must define a template.",
    )
    spec.template?.let { validatePlainTextTemplateLiteral(it) }
  }

  private fun validateDescriptionTemplateLiteral(expression: TemplateValueExpression) {
    literalString(expression)?.let { literal ->
      requireValid(
        RichTextProcessor.isPlainText(literal),
        WorkbenchErrorCode.WORK_ITEM_DESCRIPTION_TEMPLATE_MUST_BE_PLAIN_TEXT,
        "Description template must be plain text.",
      )
    }
  }

  private fun validatePlainTextTemplateLiteral(expression: TemplateValueExpression) {
    literalString(expression)?.let { literal ->
      requireValid(
        RichTextProcessor.isPlainText(literal),
        WorkbenchErrorCode.WORK_ITEM_TRANSITION_COMMENT_TEMPLATE_MUST_BE_PLAIN_TEXT,
        "Comment template must be plain text.",
      )
    }
  }

  private fun literalString(expression: TemplateValueExpression): String? =
    when (expression) {
      is TemplateValueExpression.Literal -> (expression.value as? JsonPrimitive)?.contentOrNull
      else -> null
    }

  private fun targetErrorCode(expectedTarget: WorkItemValueTemplateTarget): WorkbenchErrorCode =
    when (expectedTarget) {
      WorkItemValueTemplateTarget.CREATE ->
        WorkbenchErrorCode.WORK_ITEM_CREATE_FIELDS_TARGET_INVALID
      WorkItemValueTemplateTarget.TRANSITION ->
        WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELDS_TARGET_INVALID
    }
}
