@file:Suppress("SwallowedException")

package ink.doa.workbench.core.workitem.template

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class TransitionFieldsParser(
  private val json: Json = Json { ignoreUnknownKeys = false },
  private val valueTemplateParser: WorkItemValueTemplateParser = WorkItemValueTemplateParser(json),
) {
  fun parse(payload: String): WorkItemTransitionFieldsTemplate =
    try {
      parse(json.parseToJsonElement(payload))
    } catch (ex: SerializationException) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELDS_INVALID_JSON,
        "Invalid transition fields JSON: ${ex.message}",
      )
    }

  fun parse(element: JsonElement): WorkItemTransitionFieldsTemplate =
    parse(element, WorkItemValueTemplateTarget.TRANSITION)

  fun parseCreateFields(element: JsonElement): WorkItemTransitionFieldsTemplate =
    parse(element, WorkItemValueTemplateTarget.CREATE)

  fun parse(
    element: JsonElement,
    expectedTarget: WorkItemValueTemplateTarget,
  ): WorkItemTransitionFieldsTemplate {
    val obj = element.asObject("fields template")
    val target =
      WorkItemValueTemplateTarget.fromWireName(obj.requiredString("target"))
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_TEMPLATE_TARGET_UNKNOWN,
          "Unknown fields template target: ${obj.requiredString("target")}",
        )
    if (target != expectedTarget) {
      throw InvalidRequestException(
        when (expectedTarget) {
          WorkItemValueTemplateTarget.CREATE ->
            WorkbenchErrorCode.WORK_ITEM_CREATE_FIELDS_TARGET_INVALID
          WorkItemValueTemplateTarget.TRANSITION ->
            WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELDS_TARGET_INVALID
        },
        "Fields template target must be ${expectedTarget.wireName}.",
      )
    }
    val template =
      WorkItemTransitionFieldsTemplate(
        version = obj.requiredInt("version"),
        resource = obj.requiredString("resource"),
        target = target,
        fields =
          obj
            .requiredObject("fields")
            .mapKeys { valueTemplateParser.parseFieldPath(it.key) }
            .mapValues { parseFieldSpec(it.value) },
      )
    TransitionFieldsValidator.validateEnvelope(template, expectedTarget)
    return template
  }

  @Suppress("ThrowsCount")
  private fun parseFieldSpec(element: JsonElement): TransitionFieldSpec {
    val obj = element.asObject("field spec")
    val participation =
      FieldParticipation.fromWireName(obj.optionalString("participation") ?: "optional")
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELDS_PARTICIPATION_UNKNOWN,
          "Unknown field participation: ${obj.optionalString("participation")}",
        )
    val writeGrant =
      FieldWriteGrant.fromWireName(obj.optionalString("writeGrant") ?: "inherit")
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELDS_WRITE_GRANT_UNKNOWN,
          "Unknown field write grant: ${obj.optionalString("writeGrant")}",
        )
    val onUnauthorized =
      UnauthorizedMutationBehavior.fromWireName(
        obj.optionalString("onUnauthorized") ?: "preserve_current"
      )
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELDS_ON_UNAUTHORIZED_UNKNOWN,
          "Unknown onUnauthorized behavior: ${obj.optionalString("onUnauthorized")}",
        )
    val value =
      obj["value"]?.let { valueTemplateParser.parseExpression(it) }
        ?: obj["default"]?.let { valueTemplateParser.parseExpression(it) }
    return TransitionFieldSpec(
      participation = participation,
      value = value,
      writeGrant = writeGrant,
      onUnauthorized = onUnauthorized,
    )
  }
}

private fun JsonElement.asObject(name: String): JsonObject =
  this as? JsonObject
    ?: throw InvalidRequestException(
      WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELDS_OBJECT_REQUIRED,
      "Transition fields $name must be an object.",
    )

private fun JsonElement.asString(name: String): String =
  (this as? JsonPrimitive)?.contentOrNull
    ?: throw InvalidRequestException(
      WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELDS_STRING_REQUIRED,
      "Transition fields $name must be a string.",
    )

private fun JsonObject.required(key: String): JsonElement =
  this[key]
    ?: throw InvalidRequestException(
      WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELDS_FIELD_REQUIRED,
      "Transition fields missing required field: $key",
    )

private fun JsonObject.requiredString(key: String): String = required(key).asString(key)

private fun JsonObject.optionalString(key: String): String? =
  this[key]?.let {
    (it as? JsonPrimitive)?.contentOrNull
      ?: throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELDS_STRING_REQUIRED,
        "Transition fields $key must be a string.",
      )
  }

private fun JsonObject.requiredInt(key: String): Int =
  required(key).jsonPrimitive.intOrNull
    ?: throw InvalidRequestException(
      WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELDS_INTEGER_REQUIRED,
      "Transition fields $key must be an integer.",
    )

private fun JsonObject.requiredObject(key: String): JsonObject = required(key).jsonObject
