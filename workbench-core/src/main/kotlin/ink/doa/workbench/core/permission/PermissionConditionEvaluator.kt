package ink.doa.workbench.core.permission

import ink.doa.workbench.core.workitem.query.ConditionNode
import ink.doa.workbench.core.workitem.query.QueryField
import ink.doa.workbench.core.workitem.query.QueryOperator
import ink.doa.workbench.core.workitem.query.QueryValue
import ink.doa.workbench.core.workitem.query.WorkItemConditionJson
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

enum class PermissionConditionResult {
  MATCH,
  NO_MATCH,
  INVALID,
}

data class PermissionConditionContext(
  val actorUserId: UUID,
  val resourceAttributes: Map<String, String> = emptyMap(),
)

private typealias PredicateEvaluator = (JsonElement?, JsonElement?) -> Boolean

private typealias AttributeResolver = (PermissionConditionContext) -> JsonElement?

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

private val fieldResolvers: Map<String, AttributeResolver> =
  mapOf(
    "actor" to { JsonPrimitive(it.actorUserId.toString()) },
    "actorId" to { JsonPrimitive(it.actorUserId.toString()) },
    "reporter" to { it.resourceAttributes["reporter"]?.let(::JsonPrimitive) },
    "reporterId" to { it.resourceAttributes["reporter"]?.let(::JsonPrimitive) },
    "assignee" to { it.resourceAttributes["assignee"]?.let(::JsonPrimitive) },
    "assigneeId" to { it.resourceAttributes["assignee"]?.let(::JsonPrimitive) },
    "status" to { it.resourceAttributes["status"]?.let(::JsonPrimitive) },
    "statusId" to { it.resourceAttributes["status"]?.let(::JsonPrimitive) },
    "statusGroup" to { it.resourceAttributes["statusGroup"]?.let(::JsonPrimitive) },
    "issueType" to { it.resourceAttributes["issueType"]?.let(::JsonPrimitive) },
    "issueTypeId" to { it.resourceAttributes["issueType"]?.let(::JsonPrimitive) },
    "issueTypeConfig" to { it.resourceAttributes["issueTypeConfig"]?.let(::JsonPrimitive) },
    "issueTypeConfigId" to { it.resourceAttributes["issueTypeConfig"]?.let(::JsonPrimitive) },
    "project" to { it.resourceAttributes["project"]?.let(::JsonPrimitive) },
    "projectId" to { it.resourceAttributes["project"]?.let(::JsonPrimitive) },
  )

private val variableResolvers: Map<String, AttributeResolver> =
  mapOf(
    "user.currentUser" to { JsonPrimitive(it.actorUserId.toString()) },
    "issue.reporter" to { it.resourceAttributes["reporter"]?.let(::JsonPrimitive) ?: JsonNull },
    "issue.assignee" to { it.resourceAttributes["assignee"]?.let(::JsonPrimitive) ?: JsonNull },
  )

class PermissionConditionEvaluator {
  private val json = Json { ignoreUnknownKeys = true }

  fun evaluate(conditionJson: String?, context: PermissionConditionContext): PermissionConditionResult {
    if (conditionJson.isNullOrBlank()) return PermissionConditionResult.MATCH
    val ast =
      try {
        json.parseToJsonElement(conditionJson).jsonObject
      } catch (_: Exception) {
        return PermissionConditionResult.INVALID
      }
    val condition =
      try {
        WorkItemConditionJson.parse(ast)
      } catch (_: Exception) {
        return PermissionConditionResult.INVALID
      } ?: return PermissionConditionResult.MATCH
    return try {
      if (evaluateNode(condition, context)) {
        PermissionConditionResult.MATCH
      } else {
        PermissionConditionResult.NO_MATCH
      }
    } catch (_: Exception) {
      PermissionConditionResult.INVALID
    }
  }

  private fun evaluateNode(node: ConditionNode, context: PermissionConditionContext): Boolean =
    when (node) {
      is ConditionNode.And -> node.args.all { evaluateNode(it, context) }
      is ConditionNode.Or -> node.args.any { evaluateNode(it, context) }
      is ConditionNode.Not -> !evaluateNode(node.arg, context)
      is ConditionNode.Predicate -> evaluatePredicate(node, context)
    }

  private fun evaluatePredicate(
    predicate: ConditionNode.Predicate,
    context: PermissionConditionContext,
  ): Boolean {
    val left = resolveField(predicate.field, context)
    val right = predicate.value?.let { resolveValue(it, context) }
    return predicateEvaluators[predicate.op]?.invoke(left, right) ?: false
  }

  private fun resolveField(field: QueryField, context: PermissionConditionContext): JsonElement? {
    if (field is QueryField.Property) {
      val key = field.apiId ?: field.code
      return key?.let { context.resourceAttributes[it] }?.let(::JsonPrimitive)
    }
    val name = field.canonicalName.removePrefix("property.")
    return fieldResolvers[name]?.invoke(context)
      ?: context.resourceAttributes[name]?.let(::JsonPrimitive)
  }

  private fun resolveValue(value: QueryValue, context: PermissionConditionContext): JsonElement =
    when (value) {
      is QueryValue.Literal -> value.value
      is QueryValue.Variable ->
        variableResolvers[value.name]?.invoke(context)
          ?: throw IllegalArgumentException("Unsupported condition variable: ${value.name}")
      else -> throw IllegalArgumentException("Unsupported condition value.")
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
    ?: throw IllegalArgumentException("Condition value must be numeric.")
