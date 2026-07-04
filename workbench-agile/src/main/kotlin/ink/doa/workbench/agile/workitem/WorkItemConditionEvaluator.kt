package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.query.ConditionNode
import ink.doa.workbench.core.workitem.query.QueryField
import ink.doa.workbench.core.workitem.query.QueryOperator
import ink.doa.workbench.core.workitem.query.QueryValue
import ink.doa.workbench.core.workitem.query.WorkItemConditionJson
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

data class WorkItemConditionContext(
  val workItem: WorkItemRecord,
  val actorUserId: UUID,
  val properties: Map<String, JsonElement>,
  val childIssuesNotDone: Long = 0,
)

class WorkItemConditionEvaluator {
  fun evaluate(ast: JsonObject, context: WorkItemConditionContext): Boolean {
    val condition = WorkItemConditionJson.parse(ast) ?: return true
    return evaluateNode(condition, context)
  }

  private fun evaluateNode(node: ConditionNode, context: WorkItemConditionContext): Boolean =
    when (node) {
      is ConditionNode.And -> node.args.all { evaluateNode(it, context) }
      is ConditionNode.Or -> node.args.any { evaluateNode(it, context) }
      is ConditionNode.Not -> !evaluateNode(node.arg, context)
      is ConditionNode.Predicate -> evaluatePredicate(node, context)
    }

  @Suppress("CyclomaticComplexMethod")
  private fun evaluatePredicate(predicate: ConditionNode.Predicate, context: WorkItemConditionContext): Boolean {
    val left = resolveField(predicate.field, context)
    val right = predicate.value?.let { resolveValue(it, context) }
    return when (predicate.op) {
      QueryOperator.EQ -> scalar(left) == scalar(right)
      QueryOperator.NEQ -> scalar(left) != scalar(right)
      QueryOperator.IN -> values(right).contains(scalar(left))
      QueryOperator.NOT_IN -> !values(right).contains(scalar(left))
      QueryOperator.IS_NOT_EMPTY -> left != null && left !is JsonNull
      QueryOperator.IS_EMPTY -> left == null || left is JsonNull
      QueryOperator.GT -> number(left) > number(right)
      QueryOperator.GTE -> number(left) >= number(right)
      QueryOperator.LT -> number(left) < number(right)
      QueryOperator.LTE -> number(left) <= number(right)
      QueryOperator.CONTAINS -> scalar(left)?.contains(scalar(right).orEmpty()) == true
      QueryOperator.NOT_CONTAINS -> scalar(left)?.contains(scalar(right).orEmpty()) != true
      QueryOperator.HAS_ANY -> arrayValues(left).any { it in values(right) }
      QueryOperator.HAS_ALL -> values(right).all { it in arrayValues(left) }
      QueryOperator.HAS_NONE -> arrayValues(left).none { it in values(right) }
      else ->
        throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_CONDITION_UNSUPPORTED,
          "Unsupported condition operator: ${predicate.op.wireName}",
        )
    }
  }

  private fun resolveField(field: QueryField, context: WorkItemConditionContext): JsonElement? {
    if (field is QueryField.Property) {
      return field.apiId?.let { context.properties[it] ?: context.workItem.properties[it] }
        ?: field.code?.let { context.properties[it] ?: context.workItem.properties[it] }
    }
    val name = field.canonicalName.removePrefix("property.")
    return when (name) {
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
  }

  private fun resolveValue(value: QueryValue, context: WorkItemConditionContext): JsonElement =
    when (value) {
      is QueryValue.Literal -> value.value
      is QueryValue.Variable ->
        when (value.name) {
          "user.currentUser" -> JsonPrimitive(context.actorUserId.toString())
          "issue.reporter" -> JsonPrimitive(context.workItem.reporterId.toString())
          "issue.assignee" ->
            context.workItem.assigneeId?.let { JsonPrimitive(it.toString()) } ?: JsonNull
          else ->
            throw InvalidRequestException(
              WorkbenchErrorCode.WORK_ITEM_CONDITION_UNSUPPORTED,
              "Unsupported condition variable: ${value.name}",
            )
        }
      else ->
        throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_CONDITION_UNSUPPORTED,
          "Unsupported condition value.",
        )
    }

  private fun values(value: JsonElement?): List<String?> =
    when (value) {
      is JsonArray -> value.map { scalar(it) }
      else -> listOf(scalar(value))
    }

  private fun arrayValues(value: JsonElement?): List<String?> =
    (value as? JsonArray)?.map { scalar(it) } ?: emptyList()

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
}
