package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

data class WorkItemConditionContext(
  val workItem: WorkItemRecord,
  val actorUserId: UUID,
  val properties: Map<String, JsonElement>,
  val childIssuesNotDone: Long = 0,
)

class WorkItemConditionEvaluator {
  fun evaluate(ast: JsonObject, context: WorkItemConditionContext): Boolean {
    if (ast.isEmpty()) return true
    return evaluateNode(ast, context)
  }

  private fun evaluateNode(node: JsonObject, context: WorkItemConditionContext): Boolean =
    when {
      "all" in node -> node.array("all").all { evaluateElement(it, context) }
      "any" in node -> node.array("any").any { evaluateElement(it, context) }
      "not" in node -> !evaluateElement(node.require("not"), context)
      "field" in node -> evaluatePredicate(node, context)
      else ->
        throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_CONDITION_UNSUPPORTED,
          "Condition node must contain all, any, not, or field.",
        )
    }

  private fun evaluateElement(element: JsonElement, context: WorkItemConditionContext): Boolean =
    evaluateNode(
      element as? JsonObject
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_CONDITION_UNSUPPORTED,
          "Condition child must be an object.",
        ),
      context,
    )

  @Suppress("CyclomaticComplexMethod")
  private fun evaluatePredicate(node: JsonObject, context: WorkItemConditionContext): Boolean {
    val left = resolveField(node.string("field"), context)
    val op = node.string("op").lowercase()
    val right = node["value"]?.let { resolveValue(it, context) }
    return when (op) {
      "eq",
      "==" -> scalar(left) == scalar(right)
      "ne",
      "!=" -> scalar(left) != scalar(right)
      "in" -> node.array("value").map { scalar(resolveValue(it, context)) }.contains(scalar(left))
      "not_in" ->
        !node.array("value").map { scalar(resolveValue(it, context)) }.contains(scalar(left))
      "exists" -> left != null && left !is JsonNull
      "missing" -> left == null || left is JsonNull
      "gt" -> number(left) > number(right)
      "gte" -> number(left) >= number(right)
      "lt" -> number(left) < number(right)
      "lte" -> number(left) <= number(right)
      else ->
        throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_CONDITION_UNSUPPORTED,
          "Unsupported condition operator: $op",
        )
    }
  }

  private fun resolveField(name: String, context: WorkItemConditionContext): JsonElement? =
    when (name) {
      "actor",
      "actorId" -> JsonPrimitive(context.actorUserId.toString())
      "reporter",
      "reporterId" -> JsonPrimitive(context.workItem.reporterId.toString())
      "assignee",
      "assigneeId" -> context.workItem.assigneeId?.let { JsonPrimitive(it.toString()) }
      "status",
      "statusId" -> JsonPrimitive(context.workItem.statusApiId.value)
      "statusGroup" -> JsonPrimitive(context.workItem.statusGroup.dbValue)
      "issueType",
      "issueTypeId" -> JsonPrimitive(context.workItem.issueTypeApiId.value)
      "issueTypeConfig",
      "issueTypeConfigId" -> JsonPrimitive(context.workItem.issueTypeConfigApiId.value)
      "project",
      "projectId" -> JsonPrimitive(context.workItem.projectId.toString())
      "children.notDone" -> JsonPrimitive(context.childIssuesNotDone)
      else -> context.properties[name] ?: context.workItem.properties[name]
    }

  private fun resolveValue(value: JsonElement, context: WorkItemConditionContext): JsonElement =
    if (value is JsonPrimitive && value.isString) {
      when (value.content) {
        "user.currentUser" -> JsonPrimitive(context.actorUserId.toString())
        "issue.reporter" -> JsonPrimitive(context.workItem.reporterId.toString())
        "issue.assignee" ->
          context.workItem.assigneeId?.let { JsonPrimitive(it.toString()) } ?: JsonNull
        else -> value
      }
    } else {
      value
    }

  private fun scalar(value: JsonElement?): String? =
    when (value) {
      null,
      JsonNull -> null
      is JsonPrimitive -> value.contentOrNull ?: value.toString()
      else -> value.toString()
    }

  private fun number(value: JsonElement?): Double =
    (value as? JsonPrimitive)?.doubleOrNull
      ?: throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_CONDITION_UNSUPPORTED,
        "Condition value must be numeric.",
      )

  private fun JsonObject.require(name: String): JsonElement =
    this[name]
      ?: throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_CONDITION_UNSUPPORTED,
        "Condition missing '$name'.",
      )

  private fun JsonObject.string(name: String): String =
    require(name).jsonPrimitive.contentOrNull
      ?: throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_CONDITION_UNSUPPORTED,
        "Condition '$name' must be a string.",
      )

  private fun JsonObject.array(name: String): JsonArray =
    require(name) as? JsonArray
      ?: throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_CONDITION_UNSUPPORTED,
        "Condition '$name' must be an array.",
      )
}
