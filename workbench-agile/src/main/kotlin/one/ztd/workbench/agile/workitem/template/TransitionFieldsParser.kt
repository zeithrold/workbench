package one.ztd.workbench.agile.workitem.template

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.SerializationParseSupport
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode

class TransitionFieldsParser(
  private val json: Json = Json { ignoreUnknownKeys = false },
  private val valueTemplateParser: WorkItemValueTemplateParser = WorkItemValueTemplateParser(json),
) {
  fun parse(payload: String): WorkItemTransitionFieldsTemplate =
    SerializationParseSupport.parseOrThrow(
      { parse(json.parseToJsonElement(payload)) },
      { ex ->
        InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELDS_INVALID_JSON,
          "Invalid transition fields JSON: ${ex.message}",
        )
      },
    )

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
        comment = obj["comment"]?.let { parseCommentSpec(it) },
      )
    TransitionFieldsValidator.validateEnvelope(template, expectedTarget)
    return template
  }

  private fun parseFieldSpec(element: JsonElement): TransitionFieldSpec {
    val obj = element.asObject("field spec")
    return TransitionFieldSpec(
      participation = parseFieldParticipation(obj),
      value =
        obj["value"]?.let { valueTemplateParser.parseExpression(it) }
          ?: obj["default"]?.let { valueTemplateParser.parseExpression(it) },
      writeGrant = parseFieldWriteGrant(obj),
      onUnauthorized = parseOnUnauthorizedBehavior(obj),
    )
  }

  private fun parseFieldParticipation(obj: JsonObject): FieldParticipation {
    val wireName = obj.optionalString("participation") ?: "optional"
    return FieldParticipation.fromWireName(wireName)
      ?: throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELDS_PARTICIPATION_UNKNOWN,
        "Unknown field participation: $wireName",
      )
  }

  private fun parseFieldWriteGrant(obj: JsonObject): FieldWriteGrant {
    val wireName = obj.optionalString("writeGrant") ?: "inherit"
    return FieldWriteGrant.fromWireName(wireName)
      ?: throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELDS_WRITE_GRANT_UNKNOWN,
        "Unknown field write grant: $wireName",
      )
  }

  private fun parseOnUnauthorizedBehavior(obj: JsonObject): UnauthorizedMutationBehavior {
    val wireName = obj.optionalString("onUnauthorized") ?: "preserve_current"
    return UnauthorizedMutationBehavior.fromWireName(wireName)
      ?: throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELDS_ON_UNAUTHORIZED_UNKNOWN,
        "Unknown onUnauthorized behavior: $wireName",
      )
  }

  private fun parseCommentSpec(element: JsonElement): CommentFieldSpec {
    val obj = element.asObject("comment spec")
    val participation =
      FieldParticipation.fromWireName(obj.optionalString("participation") ?: "optional")
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELDS_PARTICIPATION_UNKNOWN,
          "Unknown comment participation: ${obj.optionalString("participation")}",
        )
    val template =
      obj["template"]?.let { valueTemplateParser.parseExpression(it) }
        ?: obj["value"]?.let { valueTemplateParser.parseExpression(it) }
    return CommentFieldSpec(participation = participation, template = template)
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
