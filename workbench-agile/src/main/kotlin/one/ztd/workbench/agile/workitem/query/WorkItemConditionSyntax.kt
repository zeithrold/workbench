package one.ztd.workbench.agile.workitem.query

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode

private val LEGACY_LOGICAL_KEYS = setOf("all", "any")
private val LEGACY_OPERATORS = setOf("==", "ne", "!=", "exists", "missing")
private val LEGACY_VARIABLE_PRIMITIVES =
  setOf("user.currentUser", "issue.reporter", "issue.assignee")
private val LEGACY_FIELD_ALIASES =
  setOf(
    "actor",
    "actorId",
    "reporter",
    "reporterId",
    "assignee",
    "assigneeId",
    "status",
    "statusId",
    "statusGroup",
    "issueType",
    "issueTypeId",
    "issueTypeConfig",
    "issueTypeConfigId",
    "project",
    "projectId",
  )
private val API_ID_ENTITY_FIELDS =
  setOf(
    "issue.reporter",
    "issue.assignee",
    "issue.status",
    "issue.issueType",
    "issue.issueTypeConfig",
    "issue.project",
  )
private val UUID_LITERAL =
  Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

object WorkItemConditionSyntax {
  fun validate(element: JsonObject) {
    if (element.isEmpty()) return
    validateNode(element)
  }

  private fun validateNode(element: JsonObject) {
    rejectLegacyLogicalShape(element)
    if ("field" in element) {
      validatePredicate(element)
      return
    }
    val op = element["op"]?.jsonPrimitive?.contentOrNull
    when (op) {
      "and",
      "or" -> element.array("args").forEach { validateNode(it.asConditionObject()) }
      "not" -> validateNode(element.getValue("arg").asConditionObject())
      null -> rejectLegacySyntax("Condition logical node requires op.")
      else -> rejectLegacySyntax("Unsupported condition logical operator: $op")
    }
  }

  private fun validatePredicate(predicate: JsonObject) {
    val field = predicate.fieldName()
    validateFieldName(field)
    val op = predicate["op"]?.jsonPrimitive?.contentOrNull
    if (op in LEGACY_OPERATORS) {
      rejectLegacySyntax("Unsupported legacy condition operator: $op")
    }
    if (op !in setOf("is_empty", "is_not_empty")) {
      predicate["value"]?.let { validateValue(field, it) }
    }
  }

  private fun validateFieldName(field: String) {
    if (field in LEGACY_FIELD_ALIASES) {
      rejectLegacySyntax("Legacy field alias '$field' is not supported.")
    }
    if (
      !field.startsWith("issue.") && !field.startsWith("property.") && field != "children.notDone"
    ) {
      rejectLegacySyntax("Unsupported condition field: $field")
    }
  }

  private fun validateValue(field: String, value: JsonElement) {
    when (value) {
      is JsonPrimitive -> validateLiteral(field, value.contentOrNull)
      is JsonArray -> value.forEach { validateValue(field, it) }
      is JsonObject -> {
        if ("var" in value) return
        value.forEach { (_, nested) -> validateValue(field, nested) }
      }
    }
  }

  private fun validateLiteral(field: String, literal: String?) {
    if (literal == null) return
    if (literal in LEGACY_VARIABLE_PRIMITIVES) {
      rejectLegacySyntax("Condition variables must use {\"var\":\"...\"} syntax.")
    }
    if (field in API_ID_ENTITY_FIELDS && UUID_LITERAL.matches(literal)) {
      throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_CONDITION_UUID_LITERAL_FORBIDDEN)
    }
  }

  private fun rejectLegacyLogicalShape(element: JsonObject) {
    if (LEGACY_LOGICAL_KEYS.any { it in element }) {
      rejectLegacySyntax("Legacy logical operators all/any are not supported.")
    }
    if ("not" in element && element["op"] == null) {
      rejectLegacySyntax("Legacy not syntax is not supported.")
    }
  }

  private fun JsonObject.fieldName(): String =
    when (val field = getValue("field")) {
      is JsonPrimitive -> field.content
      is JsonObject ->
        if (field["kind"]?.jsonPrimitive?.contentOrNull == "property") {
          val apiId = field["apiId"]?.jsonPrimitive?.contentOrNull
          val code = field["code"]?.jsonPrimitive?.contentOrNull
          "property.${apiId ?: code.orEmpty()}"
        } else {
          rejectLegacySyntax("Unsupported condition field shape.")
        }
      else -> rejectLegacySyntax("Unsupported condition field shape.")
    }

  private fun JsonObject.array(name: String): JsonArray = getValue(name) as JsonArray

  private fun JsonElement.asConditionObject(): JsonObject =
    this as? JsonObject ?: rejectLegacySyntax("Condition node must be an object.")

  private fun rejectLegacySyntax(message: String): Nothing {
    throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_CONDITION_LEGACY_SYNTAX, message)
  }
}
