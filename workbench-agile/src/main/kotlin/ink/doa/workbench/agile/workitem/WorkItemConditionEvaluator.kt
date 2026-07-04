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

private typealias PredicateEvaluator = (JsonElement?, JsonElement?) -> Boolean

private typealias FieldResolver = (WorkItemConditionContext) -> JsonElement?

private val predicateEvaluators: Map<QueryOperator, PredicateEvaluator> =
  mapOf(
    QueryOperator.EQ to { left, right -> conditionScalar(left) == conditionScalar(right) },
    QueryOperator.NEQ to { left, right -> conditionScalar(left) != conditionScalar(right) },
    QueryOperator.IN to { left, right -> conditionValues(right).contains(conditionScalar(left)) },
    QueryOperator.NOT_IN to
      { left, right ->
        !conditionValues(right).contains(conditionScalar(left))
      },
    QueryOperator.IS_NOT_EMPTY to { left, _ -> left != null && left !is JsonNull },
    QueryOperator.IS_EMPTY to { left, _ -> left == null || left is JsonNull },
    QueryOperator.GT to { left, right -> conditionNumber(left) > conditionNumber(right) },
    QueryOperator.GTE to { left, right -> conditionNumber(left) >= conditionNumber(right) },
    QueryOperator.LT to { left, right -> conditionNumber(left) < conditionNumber(right) },
    QueryOperator.LTE to { left, right -> conditionNumber(left) <= conditionNumber(right) },
    QueryOperator.CONTAINS to
      { left, right ->
        conditionScalar(left)?.contains(conditionScalar(right).orEmpty()) == true
      },
    QueryOperator.NOT_CONTAINS to
      { left, right ->
        conditionScalar(left)?.contains(conditionScalar(right).orEmpty()) != true
      },
    QueryOperator.HAS_ANY to
      { left, right ->
        conditionArrayValues(left).any { it in conditionValues(right) }
      },
    QueryOperator.HAS_ALL to
      { left, right ->
        conditionValues(right).all { it in conditionArrayValues(left) }
      },
    QueryOperator.HAS_NONE to
      { left, right ->
        conditionArrayValues(left).none { it in conditionValues(right) }
      },
  )

private val fieldResolvers: Map<String, FieldResolver> =
  mapOf(
    "actor" to { JsonPrimitive(it.actorUserId.toString()) },
    "actorId" to { JsonPrimitive(it.actorUserId.toString()) },
    "reporter" to { JsonPrimitive(it.workItem.reporterId.toString()) },
    "reporterId" to { JsonPrimitive(it.workItem.reporterId.toString()) },
    "assignee" to { it.workItem.assigneeId?.let { id -> JsonPrimitive(id.toString()) } },
    "assigneeId" to { it.workItem.assigneeId?.let { id -> JsonPrimitive(id.toString()) } },
    "status" to { JsonPrimitive(it.workItem.statusApiId.value) },
    "statusId" to { JsonPrimitive(it.workItem.statusApiId.value) },
    "statusGroup" to { JsonPrimitive(it.workItem.statusGroup.dbValue) },
    "issueType" to { JsonPrimitive(it.workItem.issueTypeApiId.value) },
    "issueTypeId" to { JsonPrimitive(it.workItem.issueTypeApiId.value) },
    "issueTypeConfig" to { JsonPrimitive(it.workItem.issueTypeConfigApiId.value) },
    "issueTypeConfigId" to { JsonPrimitive(it.workItem.issueTypeConfigApiId.value) },
    "project" to { JsonPrimitive(it.workItem.projectId.toString()) },
    "projectId" to { JsonPrimitive(it.workItem.projectId.toString()) },
    "children.notDone" to { JsonPrimitive(it.childIssuesNotDone) },
  )

private val variableResolvers: Map<String, FieldResolver> =
  mapOf(
    "user.currentUser" to { JsonPrimitive(it.actorUserId.toString()) },
    "issue.reporter" to { JsonPrimitive(it.workItem.reporterId.toString()) },
    "issue.assignee" to
      {
        it.workItem.assigneeId?.let { id -> JsonPrimitive(id.toString()) } ?: JsonNull
      },
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

  private fun evaluatePredicate(
    predicate: ConditionNode.Predicate,
    context: WorkItemConditionContext,
  ): Boolean {
    val left = resolveField(predicate.field, context)
    val right = predicate.value?.let { resolveValue(it, context) }
    return predicateEvaluators[predicate.op]?.invoke(left, right)
      ?: throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_CONDITION_UNSUPPORTED,
        "Unsupported condition operator: ${predicate.op.wireName}",
      )
  }

  private fun resolveField(field: QueryField, context: WorkItemConditionContext): JsonElement? {
    if (field is QueryField.Property) {
      return field.apiId?.let { context.properties[it] ?: context.workItem.properties[it] }
        ?: field.code?.let { context.properties[it] ?: context.workItem.properties[it] }
    }
    val name = field.canonicalName.removePrefix("property.")
    return fieldResolvers[name]?.invoke(context)
      ?: context.properties[name]
      ?: context.workItem.properties[name]
  }

  private fun resolveValue(value: QueryValue, context: WorkItemConditionContext): JsonElement =
    when (value) {
      is QueryValue.Literal -> value.value
      is QueryValue.Variable ->
        variableResolvers[value.name]?.invoke(context)
          ?: throw InvalidRequestException(
            WorkbenchErrorCode.WORK_ITEM_CONDITION_UNSUPPORTED,
            "Unsupported condition variable: ${value.name}",
          )
      else ->
        throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_CONDITION_UNSUPPORTED,
          "Unsupported condition value.",
        )
    }
}

private fun conditionValues(value: JsonElement?): List<String?> =
  when (value) {
    is JsonArray -> value.map { conditionScalar(it) }
    else -> listOf(conditionScalar(value))
  }

private fun conditionArrayValues(value: JsonElement?): List<String?> =
  (value as? JsonArray)?.map { conditionScalar(it) } ?: emptyList()

private fun conditionScalar(value: JsonElement?): String? =
  when (value) {
    null,
    JsonNull -> null
    is JsonPrimitive -> value.contentOrNull ?: value.toString()
    else -> value.toString()
  }

private fun conditionNumber(value: JsonElement?): Double =
  (value as? JsonPrimitive)?.doubleOrNull
    ?: throw InvalidRequestException(
      WorkbenchErrorCode.WORK_ITEM_CONDITION_UNSUPPORTED,
      "Condition value must be numeric.",
    )
