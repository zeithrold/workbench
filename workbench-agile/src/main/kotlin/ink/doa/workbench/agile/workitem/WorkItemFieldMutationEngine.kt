package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.PermissionDeniedException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.WorkItemCommentFormMeta
import ink.doa.workbench.core.workitem.model.WorkItemFormFieldMeta
import ink.doa.workbench.core.workitem.template.CommentFieldSpec
import ink.doa.workbench.core.workitem.template.FieldParticipation
import ink.doa.workbench.core.workitem.template.FieldWriteGrant
import ink.doa.workbench.core.workitem.template.TemplateField
import ink.doa.workbench.core.workitem.template.TransitionFieldSpec
import ink.doa.workbench.core.workitem.template.TransitionFieldsValidator
import ink.doa.workbench.core.workitem.template.UnauthorizedMutationBehavior
import ink.doa.workbench.core.workitem.template.WorkItemTransitionFieldsTemplate
import ink.doa.workbench.core.workitem.template.WorkItemValueTemplateContext
import ink.doa.workbench.core.workitem.template.WorkItemValueTemplateEvaluator
import ink.doa.workbench.core.workitem.template.WorkItemValueTemplateTarget
import ink.doa.workbench.core.workitem.template.isNonNullValue
import ink.doa.workbench.core.workitem.template.toWirePath
import java.time.Clock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.springframework.stereotype.Service

data class TransitionFieldReconcileResult(
  val propertyValues: Map<String, JsonElement>,
  val systemFields: Map<String, String?>,
)

data class FieldReconciliationContext(
  val template: WorkItemTransitionFieldsTemplate,
  val expectedTarget: WorkItemValueTemplateTarget,
  val config: IssueTypeConfigDetails,
  val templateContext: WorkItemValueTemplateContext,
  val currentProperties: Map<String, JsonElement>,
  val userProperties: Map<String, JsonElement>,
  val permissionContext: WorkItemFieldPermissionContext,
)

data class FieldFormPlan(
  val editableWirePaths: List<String>,
  val fieldMeta: List<WorkItemFormFieldMeta>,
)

data class PatchMutationContext(
  val config: IssueTypeConfigDetails,
  val permissionContext: WorkItemFieldPermissionContext,
  val propertyInputs: Map<String, JsonElement>,
  val systemFieldInputs: Map<String, String?>,
)

@Service
class WorkItemFieldMutationEngine(
  private val permissions: WorkItemFieldPermissionService,
  clock: Clock,
) {
  private val templates = WorkItemValueTemplateEvaluator(clock)

  suspend fun applyTemplate(context: FieldReconciliationContext): TransitionFieldReconcileResult {
    TransitionFieldsValidator.validate(context.template, context.config, context.expectedTarget)
    validateMutationRequest(
      context.template,
      context.config,
      context.userProperties,
    )
    val propertyValues = mutableMapOf<String, JsonElement>()
    val systemFields = mutableMapOf<String, String?>()

    context.template.fields.forEach { (field, spec) ->
      val outputKey = fieldOutputKey(field, context.config)
      val currentValue =
        currentValue(field, outputKey, context.currentProperties, context.templateContext)
      val templateValue =
        evaluateTemplateValue(field, spec, context.config, context.templateContext)
      val userValue =
        if (spec.participation == FieldParticipation.AUTOMATIC) {
          null
        } else {
          userValueForField(field, outputKey, context.userProperties)
        }

      val policy = permissions.resolvePolicy(context.permissionContext, field, spec)
      val effective =
        reconcileField(
          spec = spec,
          currentValue = currentValue,
          templateValue = templateValue,
          userValue = userValue,
          canWrite = policy.bindingAllowsWrite,
        )

      if (spec.participation == FieldParticipation.REQUIRED && !effective.isNonNullValue()) {
        if (spec.writeGrant == FieldWriteGrant.IMMUTABLE) {
          throw InvalidRequestException(
            WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELD_IMMUTABLE_BUT_REQUIRED,
            "Required field is immutable and has no value: ${field.toWirePath()}",
          )
        }
        throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_PROPERTY_REQUIRED,
          "Required field is missing: ${field.toWirePath()}",
        )
      }

      if (effective == null || effective is JsonNull) return@forEach
      when (field) {
        is TemplateField.System -> systemFields[field.canonicalName] = effective.stringOrNull()
        is TemplateField.Property -> propertyValues[outputKey] = effective
      }
    }

    return TransitionFieldReconcileResult(propertyValues, systemFields)
  }

  suspend fun planForm(
    template: WorkItemTransitionFieldsTemplate,
    config: IssueTypeConfigDetails,
    templateContext: WorkItemValueTemplateContext,
    permissionContext: WorkItemFieldPermissionContext,
  ): FieldFormPlan {
    val editableWirePaths = mutableListOf<String>()
    val fieldMeta = mutableListOf<WorkItemFormFieldMeta>()
    template.fields.forEach { (field, spec) ->
      val policy = permissions.resolvePolicy(permissionContext, field, spec)
      if (policy.allowsFormEdit(spec)) {
        editableWirePaths += field.toWirePath()
      }
      fieldMeta +=
        WorkItemFormFieldMeta(
          path = field.toWirePath(),
          editable = policy.allowsFormEdit(spec),
          participation = spec.participation.wireName,
          defaultValue = evaluateTemplateValue(field, spec, config, templateContext),
        )
    }
    return FieldFormPlan(editableWirePaths, fieldMeta)
  }

  suspend fun assertPatch(context: PatchMutationContext) {
    context.propertyInputs.forEach { (key, _) ->
      val field = propertyField(key, context.config)
      val policy = permissions.resolvePatchPolicy(context.permissionContext, field)
      if (!policy.allowsPatchSubmission()) {
        throw PermissionDeniedException(
          WorkbenchErrorCode.WORK_ITEM_FIELD_WRITE_DENIED,
          "Work item field write permission denied: $key",
        )
      }
    }
    context.systemFieldInputs.forEach { (name, value) ->
      if (value == null) return@forEach
      val policy =
        permissions.resolvePatchPolicy(
          context.permissionContext,
          TemplateField.System(name),
        )
      if (!policy.allowsPatchSubmission()) {
        throw PermissionDeniedException(
          WorkbenchErrorCode.WORK_ITEM_FIELD_WRITE_DENIED,
          "Work item field write permission denied: $name",
        )
      }
    }
  }

  private fun reconcileField(
    spec: TransitionFieldSpec,
    currentValue: JsonElement?,
    templateValue: JsonElement?,
    userValue: JsonElement?,
    canWrite: Boolean,
  ): JsonElement? =
    TransitionFieldReconcileSupport.reconcileField(
      ReconcileFieldParams(
        spec = spec,
        currentValue = currentValue,
        templateValue = templateValue,
        userValue = userValue,
        canWrite = canWrite,
        handleUnauthorized = ::handleUnauthorized,
      )
    )

  private fun validateMutationRequest(
    template: WorkItemTransitionFieldsTemplate,
    config: IssueTypeConfigDetails,
    userProperties: Map<String, JsonElement>,
  ) {
    userProperties.forEach { (key, value) ->
      if (!isSubmitted(value)) return@forEach
      val field = resolveRequestField(key, template, config) ?: throwUnexpectedField(key)
      val spec = template.fields[field] ?: throwUnexpectedField(key)
      if (spec.participation == FieldParticipation.AUTOMATIC) {
        throw PermissionDeniedException(
          WorkbenchErrorCode.WORK_ITEM_MUTATION_FIELD_NOT_EDITABLE,
          "Automatic field is not editable in this mutation request: ${field.toWirePath()}",
        )
      }
    }
  }

  private fun handleUnauthorized(
    spec: TransitionFieldSpec,
    currentValue: JsonElement?,
    templateValue: JsonElement?,
    userSubmitted: Boolean,
  ): JsonElement? =
    when (spec.onUnauthorized) {
      UnauthorizedMutationBehavior.REJECT ->
        if (userSubmitted) {
          throw PermissionDeniedException(
            WorkbenchErrorCode.WORK_ITEM_TRANSITION_UNAUTHORIZED_FIELD_MUTATION,
            "Unauthorized field mutation during transition.",
          )
        } else {
          currentValue
        }
      UnauthorizedMutationBehavior.APPLY_DEFAULT_ONLY ->
        if (userSubmitted) {
          templateValue ?: currentValue
        } else {
          templateValue ?: currentValue
        }
      UnauthorizedMutationBehavior.PRESERVE_CURRENT -> currentValue
    }

  private fun evaluateTemplateValue(
    field: TemplateField,
    spec: TransitionFieldSpec,
    config: IssueTypeConfigDetails,
    context: WorkItemValueTemplateContext,
  ): JsonElement? {
    val expression = spec.value ?: return null
    val property =
      (field as? TemplateField.Property)?.let {
        config.properties.singleOrNull { p ->
          p.code == it.code || p.propertyApiId.value == it.apiId
        }
      }
    return templates.evaluateExpression(expression, property, context)
  }

  fun reconcileTransitionComment(
    spec: CommentFieldSpec?,
    templateContext: WorkItemValueTemplateContext,
    userComment: String?,
  ): String? {
    validateTransitionCommentSpec(spec, userComment)
    if (spec == null) return null
    val templateComment =
      spec.template?.let { expression ->
        templates
          .evaluateExpression(expression, targetProperty = null, context = templateContext)
          .stringOrNull()
      }
    val effective =
      userComment?.takeIf { it.isNotBlank() } ?: templateComment?.takeIf { it.isNotBlank() }
    if (spec.participation == FieldParticipation.REQUIRED && effective.isNullOrBlank()) {
      throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_TRANSITION_COMMENT_REQUIRED)
    }
    return effective?.takeIf { it.isNotBlank() }
  }

  private fun validateTransitionCommentSpec(spec: CommentFieldSpec?, userComment: String?) {
    when {
      spec == null && !userComment.isNullOrBlank() ->
        throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_MUTATION_UNEXPECTED_FIELD,
          "Unexpected transition comment.",
        )
      spec?.participation == FieldParticipation.AUTOMATIC ->
        throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_TRANSITION_COMMENT_PARTICIPATION_INVALID
        )
    }
  }

  fun buildCommentMeta(
    spec: CommentFieldSpec?,
    templateContext: WorkItemValueTemplateContext,
  ): WorkItemCommentFormMeta? {
    if (spec == null) return null
    val defaultTemplate =
      spec.template?.let { expression ->
        templates
          .evaluateExpression(expression, targetProperty = null, context = templateContext)
          .stringOrNull()
      }
    return WorkItemCommentFormMeta(
      participation = spec.participation.wireName,
      editable = spec.participation != FieldParticipation.AUTOMATIC,
      defaultTemplate = defaultTemplate,
    )
  }
}

private fun throwUnexpectedField(key: String): Nothing =
  throw InvalidRequestException(
    WorkbenchErrorCode.WORK_ITEM_MUTATION_UNEXPECTED_FIELD,
    "Unexpected field in mutation request: $key",
  )

private fun resolveRequestField(
  key: String,
  template: WorkItemTransitionFieldsTemplate,
  config: IssueTypeConfigDetails,
): TemplateField? =
  template.fields.keys.firstOrNull { field -> requestKeyMatchesField(key, field, config) }

private fun requestKeyMatchesField(
  key: String,
  field: TemplateField,
  config: IssueTypeConfigDetails,
): Boolean =
  when (field) {
    is TemplateField.System -> key == field.canonicalName
    is TemplateField.Property -> {
      val outputKey = fieldOutputKey(field, config)
      key == outputKey ||
        key == field.code ||
        key == field.apiId ||
        key == field.toWirePath() ||
        key == "property.$outputKey" ||
        (field.apiId != null && key == "property.${field.apiId}")
    }
  }

private fun isSubmitted(value: JsonElement): Boolean = value !is JsonNull

private fun currentValue(
  field: TemplateField,
  outputKey: String,
  currentProperties: Map<String, JsonElement>,
  context: WorkItemValueTemplateContext,
): JsonElement? =
  when (field) {
    is TemplateField.System ->
      resolveSystemCurrent(field.canonicalName, context)?.let(::JsonPrimitive)
    is TemplateField.Property -> currentProperties[outputKey]
  }

private fun userValueForField(
  field: TemplateField,
  outputKey: String,
  userProperties: Map<String, JsonElement>,
): JsonElement? =
  when (field) {
    is TemplateField.System -> userProperties[field.canonicalName]
    is TemplateField.Property ->
      userProperties[outputKey] ?: userProperties[field.apiId ?: field.code ?: outputKey]
  }

private fun fieldOutputKey(field: TemplateField, config: IssueTypeConfigDetails): String =
  when (field) {
    is TemplateField.System -> field.canonicalName
    is TemplateField.Property ->
      config.properties
        .singleOrNull { it.code == field.code || it.propertyApiId.value == field.apiId }
        ?.code
        ?: field.code
        ?: checkNotNull(field.apiId) { "Property field must have code or apiId" }
  }

private fun propertyField(key: String, config: IssueTypeConfigDetails): TemplateField {
  val property =
    config.properties.find { it.code == key || it.propertyApiId.value == key }
      ?: throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_PROPERTY_UNAVAILABLE,
        "Property is not available in this config: $key",
      )
  return TemplateField.Property(apiId = property.propertyApiId.value, code = property.code)
}

private fun resolveSystemCurrent(name: String, context: WorkItemValueTemplateContext): String? =
  when (name) {
    "title" -> context.workItem?.title
    "description" -> context.workItem?.description
    "assignee" -> context.workItem?.assigneeApiId?.value
    "priority" -> context.workItem?.priorityApiId?.value
    "sprint" -> context.workItem?.sprintApiId?.value
    else -> null
  }

private fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.content
