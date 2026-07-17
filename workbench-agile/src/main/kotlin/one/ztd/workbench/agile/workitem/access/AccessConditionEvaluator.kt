package one.ztd.workbench.agile.workitem.access

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import one.ztd.workbench.agile.workitem.query.ConditionNode
import one.ztd.workbench.agile.workitem.query.QueryField
import one.ztd.workbench.agile.workitem.query.QueryOperator
import one.ztd.workbench.agile.workitem.query.QueryValue
import one.ztd.workbench.agile.workitem.query.WorkItemConditionJson
import one.ztd.workbench.identity.permission.PermissionConditionResult
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode

private typealias AccessFieldResolver = (AccessConditionContext) -> JsonElement?

private typealias AccessPredicateEvaluator = (JsonElement?, JsonElement?) -> Boolean

private val accessPredicateEvaluators: Map<QueryOperator, AccessPredicateEvaluator> =
  mapOf(
    QueryOperator.EQ to
      { left, right ->
        accessConditionScalar(left) == accessConditionScalar(right)
      },
    QueryOperator.NEQ to
      { left, right ->
        accessConditionScalar(left) != accessConditionScalar(right)
      },
    QueryOperator.IN to
      { left, right ->
        accessConditionValues(right).contains(accessConditionScalar(left))
      },
    QueryOperator.NOT_IN to
      { left, right ->
        !accessConditionValues(right).contains(accessConditionScalar(left))
      },
    QueryOperator.IS_NOT_EMPTY to { left, _ -> left != null && left !is JsonNull },
    QueryOperator.IS_EMPTY to { left, _ -> left == null || left is JsonNull },
    QueryOperator.GT to
      { left, right ->
        accessConditionNumber(left) > accessConditionNumber(right)
      },
    QueryOperator.GTE to
      { left, right ->
        accessConditionNumber(left) >= accessConditionNumber(right)
      },
    QueryOperator.LT to
      { left, right ->
        accessConditionNumber(left) < accessConditionNumber(right)
      },
    QueryOperator.LTE to
      { left, right ->
        accessConditionNumber(left) <= accessConditionNumber(right)
      },
    QueryOperator.CONTAINS to
      { left, right ->
        accessConditionScalar(left)?.contains(accessConditionScalar(right).orEmpty()) == true
      },
    QueryOperator.NOT_CONTAINS to
      { left, right ->
        accessConditionScalar(left)?.contains(accessConditionScalar(right).orEmpty()) != true
      },
    QueryOperator.HAS_ANY to
      { left, right ->
        accessConditionArrayValues(left).any { it in accessConditionValues(right) }
      },
    QueryOperator.HAS_ALL to
      { left, right ->
        accessConditionValues(right).all { it in accessConditionArrayValues(left) }
      },
    QueryOperator.HAS_NONE to
      { left, right ->
        accessConditionArrayValues(left).none { it in accessConditionValues(right) }
      },
  )

private val accessFieldResolvers: Map<String, AccessFieldResolver> =
  mapOf(
    "user.currentUser" to { JsonPrimitive(it.actorUserApiId) },
    "issue.reporter" to
      {
        it.workItem?.reporterApiId?.value?.let(::JsonPrimitive)
          ?: it.resourceAttributes["reporter"]?.let(::JsonPrimitive)
          ?: JsonNull
      },
    "issue.assignee" to
      {
        it.workItem?.assigneeApiId?.value?.let(::JsonPrimitive)
          ?: it.resourceAttributes["assignee"]?.let(::JsonPrimitive)
          ?: JsonNull
      },
    "issue.status" to
      {
        it.workItem?.statusApiId?.value?.let(::JsonPrimitive)
          ?: it.resourceAttributes["status"]?.let(::JsonPrimitive)
      },
    "issue.statusGroup" to
      {
        it.workItem?.statusGroup?.dbValue?.let(::JsonPrimitive)
          ?: it.resourceAttributes["statusGroup"]?.let(::JsonPrimitive)
      },
    "issue.issueType" to
      {
        it.workItem?.issueTypeApiId?.value?.let(::JsonPrimitive)
          ?: it.resourceAttributes["issueType"]?.let(::JsonPrimitive)
      },
    "issue.issueTypeConfig" to
      {
        it.workItem?.issueTypeConfigApiId?.value?.let(::JsonPrimitive)
          ?: it.resourceAttributes["issueTypeConfig"]?.let(::JsonPrimitive)
      },
    "issue.project" to
      {
        it.projectApiId?.let(::JsonPrimitive)
          ?: it.resourceAttributes["project"]?.let(::JsonPrimitive)
      },
    "children.notDone" to { JsonPrimitive(it.childIssuesNotDone) },
  )

private val accessVariableResolvers: Map<String, AccessFieldResolver> =
  mapOf(
    "user.currentUser" to { JsonPrimitive(it.actorUserApiId) },
    "issue.reporter" to
      {
        it.workItem?.reporterApiId?.value?.let(::JsonPrimitive)
          ?: it.resourceAttributes["reporter"]?.let(::JsonPrimitive)
          ?: JsonNull
      },
    "issue.assignee" to
      {
        it.workItem?.assigneeApiId?.value?.let(::JsonPrimitive)
          ?: it.resourceAttributes["assignee"]?.let(::JsonPrimitive)
          ?: JsonNull
      },
  )

class AccessConditionEvaluator {
  private val json = Json { ignoreUnknownKeys = true }

  fun evaluateObject(condition: JsonObject, context: AccessConditionContext): Boolean {
    val ast = WorkItemConditionJson.parse(condition) ?: return true
    return evaluateNode(ast, context)
  }

  fun evaluateJsonString(
    conditionJson: String?,
    context: AccessConditionContext,
  ): PermissionConditionResult {
    if (conditionJson.isNullOrBlank()) return PermissionConditionResult.MATCH
    val ast =
      try {
        json.parseToJsonElement(conditionJson).jsonObject
      } catch (_: Exception) {
        return PermissionConditionResult.INVALID
      }
    val condition = WorkItemConditionJson.parse(ast) ?: return PermissionConditionResult.INVALID
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

  private fun evaluateNode(node: ConditionNode, context: AccessConditionContext): Boolean =
    when (node) {
      is ConditionNode.And -> node.args.all { evaluateNode(it, context) }
      is ConditionNode.Or -> node.args.any { evaluateNode(it, context) }
      is ConditionNode.Not -> !evaluateNode(node.arg, context)
      is ConditionNode.Predicate -> evaluatePredicate(node, context)
    }

  private fun evaluatePredicate(
    predicate: ConditionNode.Predicate,
    context: AccessConditionContext,
  ): Boolean {
    val left = resolveField(predicate.field, context)
    val right = predicate.value?.let { resolveValue(it, context) }
    return accessPredicateEvaluators[predicate.op]?.invoke(left, right)
      ?: throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_CONDITION_UNSUPPORTED,
        "Unsupported condition operator: ${predicate.op.wireName}",
      )
  }

  private fun resolveField(field: QueryField, context: AccessConditionContext): JsonElement? {
    if (field is QueryField.Property) {
      val key = field.apiId ?: field.code
      return key?.let { context.properties[it] ?: context.workItem?.properties?.get(it) }
    }
    val name = field.canonicalName.removePrefix("property.")
    return accessFieldResolvers[name]?.invoke(context)
      ?: context.properties[name]
      ?: context.workItem?.properties?.get(name)
      ?: context.resourceAttributes[name]?.let(::JsonPrimitive)
  }

  private fun resolveValue(value: QueryValue, context: AccessConditionContext): JsonElement =
    when (value) {
      is QueryValue.Literal -> value.value
      is QueryValue.Variable ->
        accessVariableResolvers[value.name]?.invoke(context)
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

private fun accessConditionValues(value: JsonElement?): List<String?> =
  when (value) {
    is JsonArray -> value.map { accessConditionScalar(it) }
    else -> listOf(accessConditionScalar(value))
  }

private fun accessConditionArrayValues(value: JsonElement?): List<String?> =
  (value as? JsonArray)?.map { accessConditionScalar(it) }.orEmpty()

private fun accessConditionScalar(value: JsonElement?): String? =
  when (value) {
    null,
    JsonNull -> null
    is JsonPrimitive -> value.contentOrNull ?: value.toString()
    else -> value.toString()
  }

private fun accessConditionNumber(value: JsonElement?): Double =
  (value as? JsonPrimitive)?.doubleOrNull
    ?: throw InvalidRequestException(
      WorkbenchErrorCode.WORK_ITEM_CONDITION_UNSUPPORTED,
      "Condition value must be numeric.",
    )
