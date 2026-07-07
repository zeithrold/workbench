package ink.doa.workbench.core.workitem.template

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.SerializationParseSupport
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class WorkItemValueTemplateParser(private val json: Json = Json { ignoreUnknownKeys = false }) {
  fun parse(payload: String): WorkItemValueTemplate =
    SerializationParseSupport.parseOrThrow(
      { parse(json.parseToJsonElement(payload)) },
      { ex ->
        InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_TEMPLATE_INVALID_JSON,
          "Invalid work item value template JSON: ${ex.message}",
        )
      },
    )

  fun parse(element: JsonElement): WorkItemValueTemplate {
    val obj = element.asObject("template")
    val target =
      WorkItemValueTemplateTarget.fromWireName(obj.requiredString("target"))
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_TEMPLATE_TARGET_UNKNOWN,
          "Unknown work item value template target: ${obj.requiredString("target")}",
        )
    val template =
      WorkItemValueTemplate(
        version = obj.requiredInt("version"),
        resource = obj.requiredString("resource"),
        target = target,
        values =
          obj
            .requiredObject("values")
            .mapKeys { parseFieldPath(it.key) }
            .mapValues { parseExpression(it.value) },
      )
    WorkItemValueTemplateValidator.validateEnvelope(template)
    return template
  }

  fun parseExpression(element: JsonElement): TemplateValueExpression =
    when (element) {
      is JsonObject -> {
        element["var"]?.let {
          return TemplateValueExpression.Variable(it.asString("variable"))
        }
        element["relativeDate"]?.let {
          return parseRelativeDate(it)
        }
        element["copy"]?.let {
          return TemplateValueExpression.Copy(parseFieldPath(it.asString("copy")))
        }
        element["clear"]?.let {
          if (it.jsonPrimitive.booleanOrNull == true) return TemplateValueExpression.Clear
        }
        TemplateValueExpression.Literal(element)
      }
      else -> TemplateValueExpression.Literal(element)
    }

  fun parseFieldPath(path: String): TemplateField = parseTemplateFieldPath(path)

  private fun parseRelativeDate(element: JsonElement): TemplateValueExpression.RelativeDate {
    val obj = element.asObject("relativeDate")
    val unit =
      TemplateRelativeDateUnit.fromWireName(obj.requiredString("unit"))
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_TEMPLATE_RELATIVE_DATE_UNIT_UNKNOWN,
          "Unknown relative date unit: ${obj.requiredString("unit")}",
        )
    val direction =
      TemplateDateDirection.fromWireName(obj.requiredString("direction"))
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_TEMPLATE_RELATIVE_DATE_DIRECTION_UNKNOWN,
          "Unknown relative date direction: ${obj.requiredString("direction")}",
        )
    return TemplateValueExpression.RelativeDate(
      amount = obj.requiredInt("amount"),
      unit = unit,
      direction = direction,
      anchor = obj.requiredString("anchor"),
    )
  }
}

private fun JsonElement.asObject(name: String): JsonObject =
  this as? JsonObject
    ?: throw InvalidRequestException(
      WorkbenchErrorCode.WORK_ITEM_TEMPLATE_OBJECT_REQUIRED,
      "Work item value template $name must be an object.",
    )

private fun JsonElement.asString(name: String): String =
  (this as? JsonPrimitive)?.contentOrNull
    ?: throw InvalidRequestException(
      WorkbenchErrorCode.WORK_ITEM_TEMPLATE_STRING_REQUIRED,
      "Work item value template $name must be a string.",
    )

private fun JsonObject.required(key: String): JsonElement =
  this[key]
    ?: throw InvalidRequestException(
      WorkbenchErrorCode.WORK_ITEM_TEMPLATE_FIELD_REQUIRED,
      "Work item value template missing required field: $key",
    )

private fun JsonObject.requiredString(key: String): String = required(key).asString(key)

private fun JsonObject.requiredInt(key: String): Int =
  required(key).jsonPrimitive.intOrNull
    ?: throw InvalidRequestException(
      WorkbenchErrorCode.WORK_ITEM_TEMPLATE_INTEGER_REQUIRED,
      "Work item value template $key must be an integer.",
    )

private fun JsonObject.requiredObject(key: String): JsonObject = required(key).jsonObject
