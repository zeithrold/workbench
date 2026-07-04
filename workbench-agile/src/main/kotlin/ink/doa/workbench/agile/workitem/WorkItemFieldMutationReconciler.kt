package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.PermissionDeniedException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
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

@Service
@Suppress("TooManyFunctions")
class WorkItemFieldMutationReconciler(
  private val fieldPermissions: WorkItemFieldPermissionService,
  clock: Clock,
) {
  private val templates = WorkItemValueTemplateEvaluator(clock)

  suspend fun reconcileTransition(
    template: WorkItemTransitionFieldsTemplate,
    config: IssueTypeConfigDetails,
    templateContext: WorkItemValueTemplateContext,
    currentProperties: Map<String, JsonElement>,
    userProperties: Map<String, JsonElement>,
    permissionContext: WorkItemFieldPermissionContext,
  ): TransitionFieldReconcileResult =
    reconcileFields(
      template = template,
      expectedTarget = WorkItemValueTemplateTarget.TRANSITION,
      config = config,
      templateContext = templateContext,
      currentProperties = currentProperties,
      userProperties = userProperties,
      permissionContext = permissionContext.copy(operation = FieldPermissionOperation.UPDATE),
    )

  suspend fun reconcileCreate(
    template: WorkItemTransitionFieldsTemplate,
    config: IssueTypeConfigDetails,
    templateContext: WorkItemValueTemplateContext,
    userProperties: Map<String, JsonElement>,
    permissionContext: WorkItemFieldPermissionContext,
  ): TransitionFieldReconcileResult =
    reconcileFields(
      template = template,
      expectedTarget = WorkItemValueTemplateTarget.CREATE,
      config = config,
      templateContext = templateContext,
      currentProperties = emptyMap(),
      userProperties = userProperties,
      permissionContext = permissionContext.copy(operation = FieldPermissionOperation.CREATE),
    )

  suspend fun reconcileFields(
    template: WorkItemTransitionFieldsTemplate,
    expectedTarget: WorkItemValueTemplateTarget,
    config: IssueTypeConfigDetails,
    templateContext: WorkItemValueTemplateContext,
    currentProperties: Map<String, JsonElement>,
    userProperties: Map<String, JsonElement>,
    permissionContext: WorkItemFieldPermissionContext,
  ): TransitionFieldReconcileResult {
    TransitionFieldsValidator.validate(template, config, expectedTarget)
    assertMutationRequestAllowed(template, config, userProperties, permissionContext)
    val propertyValues = mutableMapOf<String, JsonElement>()
    val systemFields = mutableMapOf<String, String?>()

    template.fields.forEach { (field, spec) ->
      val outputKey = fieldOutputKey(field, config)
      val currentValue = currentValue(field, outputKey, currentProperties, templateContext)
      val templateValue = evaluateTemplateValue(field, spec, config, templateContext)
      val userValue =
        if (spec.participation == FieldParticipation.AUTOMATIC) {
          null
        } else {
          userValueForField(field, outputKey, userProperties)
        }

      val effective =
        reconcileField(
          spec = spec,
          currentValue = currentValue,
          templateValue = templateValue,
          userValue = userValue,
          canWrite = fieldPermissions.canWriteField(permissionContext, field),
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

  suspend fun assertWritableProperties(
    context: WorkItemFieldPermissionContext,
    config: IssueTypeConfigDetails,
    properties: Map<String, JsonElement>,
  ) {
    properties.forEach { (key, _) ->
      val field = propertyField(key, config)
      if (!fieldPermissions.canWriteField(context, field)) {
        throw PermissionDeniedException(
          WorkbenchErrorCode.WORK_ITEM_FIELD_WRITE_DENIED,
          "Work item field write permission denied: $key",
        )
      }
    }
  }

  suspend fun assertWritableSystemFields(
    context: WorkItemFieldPermissionContext,
    fields: Map<String, String?>,
  ) {
    fields.forEach { (name, value) ->
      if (value == null) return@forEach
      if (!fieldPermissions.canWriteField(context, TemplateField.System(name))) {
        throw PermissionDeniedException(
          WorkbenchErrorCode.WORK_ITEM_FIELD_WRITE_DENIED,
          "Work item field write permission denied: $name",
        )
      }
    }
  }

  @Suppress("CyclomaticComplexMethod")
  private fun reconcileField(
    spec: TransitionFieldSpec,
    currentValue: JsonElement?,
    templateValue: JsonElement?,
    userValue: JsonElement?,
    canWrite: Boolean,
  ): JsonElement? {
    val userSubmitted = userValue != null && userValue !is JsonNull
    if (
      spec.participation == FieldParticipation.AUTOMATIC ||
        spec.writeGrant == FieldWriteGrant.SYSTEM_ONLY
    ) {
      return templateValue ?: currentValue
    }
    return when (spec.writeGrant) {
      FieldWriteGrant.IMMUTABLE -> currentValue
      FieldWriteGrant.SYSTEM_ONLY -> templateValue ?: currentValue
      FieldWriteGrant.TRANSITION_WRITABLE ->
        when {
          userSubmitted -> userValue
          templateValue != null && templateValue !is JsonNull -> templateValue
          else -> currentValue
        }
      FieldWriteGrant.INHERIT ->
        if (canWrite) {
          when {
            userSubmitted -> userValue
            templateValue != null && templateValue !is JsonNull -> templateValue
            else -> currentValue
          }
        } else if (userSubmitted) {
          handleUnauthorized(spec, currentValue, templateValue, userSubmitted = true)
        } else {
          templateValue ?: currentValue
        }
    }
  }

  private suspend fun assertMutationRequestAllowed(
    template: WorkItemTransitionFieldsTemplate,
    config: IssueTypeConfigDetails,
    userProperties: Map<String, JsonElement>,
    permissionContext: WorkItemFieldPermissionContext,
  ) {
    userProperties.forEach { (key, value) ->
      if (!isSubmitted(value)) return@forEach
      val field = resolveRequestField(key, template, config) ?: throwUnexpectedField(key)
      val spec = template.fields[field] ?: throwUnexpectedField(key)
      if (!fieldPermissions.isFormFieldEditable(permissionContext, field, spec)) {
        throw PermissionDeniedException(
          WorkbenchErrorCode.WORK_ITEM_MUTATION_FIELD_NOT_EDITABLE,
          "Field is not editable in this mutation request: ${field.toWirePath()}",
        )
      }
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
          ?.code ?: field.code ?: field.apiId!!
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
}
