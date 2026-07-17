package one.ztd.workbench.agile.workitem.query

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

object WorkItemGroupKeySupport {
  fun keyFromBucketValue(field: QueryField, value: Any?): WorkItemGroupKey =
    if (value == null) {
      ConditionNode.Predicate(field = field, op = QueryOperator.IS_EMPTY, value = null)
    } else {
      ConditionNode.Predicate(
        field = field,
        op = QueryOperator.EQ,
        value = QueryValue.Literal(toJsonElement(value)),
      )
    }

  fun bucketValueFromKey(key: WorkItemGroupKey): Any? =
    when (key.op) {
      QueryOperator.IS_EMPTY -> null
      QueryOperator.EQ ->
        when (val value = key.value) {
          is QueryValue.Literal -> toJdbcValue(value.value)
          else -> null
        }
      else -> null
    }

  fun toJsonElement(value: Any?): JsonElement =
    when (value) {
      null -> JsonNull
      is String -> JsonPrimitive(value)
      is Number -> JsonPrimitive(value)
      is Boolean -> JsonPrimitive(value)
      else -> JsonPrimitive(value.toString())
    }

  fun toJdbcValue(element: JsonElement): Any? =
    when (element) {
      is JsonNull -> null
      is JsonPrimitive -> {
        element.booleanOrNull ?: element.longOrNull ?: element.doubleOrNull ?: element.contentOrNull
      }
      else -> element.toString()
    }
}
