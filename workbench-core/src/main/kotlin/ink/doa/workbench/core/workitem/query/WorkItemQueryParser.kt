package ink.doa.workbench.core.workitem.query

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.SerializationParseSupport
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class WorkItemQueryParser(private val json: Json = Json { ignoreUnknownKeys = false }) {
  fun parse(payload: String): WorkItemQuery =
    SerializationParseSupport.parseOrThrow(
      { parse(json.parseToJsonElement(payload)) },
      { ex ->
        InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_QUERY_INVALID_JSON,
          "Invalid work item query JSON: ${ex.message}",
        )
      },
    )

  fun parse(element: JsonElement): WorkItemQuery {
    val obj = element.asObject("query")
    val query =
      WorkItemQuery(
        version = obj.requiredInt("version"),
        resource = obj.requiredString("resource"),
        where = obj["where"]?.let(::parseCondition),
        sort = obj["sort"]?.let(::parseSort).orEmpty(),
      )
    WorkItemQueryValidator().validateEnvelope(query)
    return query
  }

  fun parseCondition(element: JsonElement): ConditionNode {
    val obj = element.asObject("condition")
    val field = obj["field"]
    if (field != null) {
      val op = parseOperator(obj.requiredString("op"))
      return ConditionNode.Predicate(
        field = parseField(field),
        op = op,
        value = obj["value"]?.let(::parseValue),
      )
    }
    return when (val op = obj.requiredString("op")) {
      "and" -> ConditionNode.And(obj.requiredArray("args").map(::parseCondition))
      "or" -> ConditionNode.Or(obj.requiredArray("args").map(::parseCondition))
      "not" -> ConditionNode.Not(parseCondition(obj.required("arg")))
      else ->
        throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_QUERY_LOGICAL_OPERATOR_UNKNOWN,
          "Unknown work item query logical operator: $op",
        )
    }
  }

  private fun parseSort(element: JsonElement): List<SortTerm> =
    element.asArray("sort").map { item ->
      val obj = item.asObject("sort term")
      val direction =
        SortDirection.fromWireName(obj.requiredString("direction"))
          ?: throw InvalidRequestException(
            WorkbenchErrorCode.WORK_ITEM_QUERY_SORT_DIRECTION_UNKNOWN,
            "Unknown work item sort direction: ${obj.requiredString("direction")}",
          )
      val nulls =
        obj["nulls"]?.let {
          val value = it.asString("sort null ordering")
          NullOrdering.fromWireName(value)
            ?: throw InvalidRequestException(
              WorkbenchErrorCode.WORK_ITEM_QUERY_SORT_NULL_ORDERING_UNKNOWN,
              "Unknown work item sort null ordering: $value",
            )
        }
      SortTerm(field = parseField(obj.required("field")), direction = direction, nulls = nulls)
    }

  private fun parseField(element: JsonElement): QueryField =
    when (element) {
      is JsonPrimitive -> parseFieldPath(element.content)
      is JsonObject -> {
        val kind = element.requiredString("kind")
        if (kind != "property") {
          throw InvalidRequestException(
            WorkbenchErrorCode.WORK_ITEM_QUERY_FIELD_KIND_UNKNOWN,
            "Unknown work item query field kind: $kind",
          )
        }
        QueryField.Property(
          apiId = element["apiId"]?.asString("property apiId"),
          code = element["code"]?.asString("property code"),
        )
      }
      else -> throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_QUERY_FIELD_SHAPE_INVALID)
    }

  private fun parseFieldPath(path: String): QueryField =
    if (path.startsWith("property.")) {
      val identity =
        path.removePrefix("property.").ifBlank {
          throw InvalidRequestException(
            WorkbenchErrorCode.WORK_ITEM_QUERY_PROPERTY_IDENTITY_REQUIRED
          )
        }
      if (identity.startsWith("fld_")) {
        QueryField.Property(apiId = identity, code = null)
      } else {
        QueryField.Property(apiId = null, code = identity)
      }
    } else {
      QueryField.System(path)
    }

  private fun parseOperator(value: String): QueryOperator =
    QueryOperator.fromWireName(value)
      ?: throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_UNKNOWN,
        "Unknown work item query operator: $value",
      )

  private fun parseValue(element: JsonElement): QueryValue =
    when (element) {
      is JsonObject -> {
        element["var"]?.let {
          return QueryValue.Variable(it.asString("variable"))
        }
        element["relativeDate"]?.let {
          return parseRelativeDate(it)
        }
        if ("from" in element || "to" in element) {
          val from = element["from"]?.takeUnless { it is JsonNull }
          val to = element["to"]?.takeUnless { it is JsonNull }
          return QueryValue.Between(from = from, to = to)
        }
        QueryValue.Literal(element)
      }
      else -> QueryValue.Literal(element)
    }

  private fun parseRelativeDate(element: JsonElement): QueryValue.RelativeDate {
    val obj = element.asObject("relativeDate")
    val unit =
      RelativeDateUnit.fromWireName(obj.requiredString("unit"))
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_QUERY_RELATIVE_DATE_UNIT_UNKNOWN,
          "Unknown relative date unit: ${obj.requiredString("unit")}",
        )
    val direction =
      DateDirection.fromWireName(obj.requiredString("direction"))
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_QUERY_RELATIVE_DATE_DIRECTION_UNKNOWN,
          "Unknown relative date direction: ${obj.requiredString("direction")}",
        )
    return QueryValue.RelativeDate(
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
      WorkbenchErrorCode.WORK_ITEM_QUERY_OBJECT_REQUIRED,
      "Work item query $name must be an object.",
    )

private fun JsonElement.asArray(name: String): JsonArray =
  this as? JsonArray
    ?: throw InvalidRequestException(
      WorkbenchErrorCode.WORK_ITEM_QUERY_ARRAY_REQUIRED,
      "Work item query $name must be an array.",
    )

private fun JsonElement.asString(name: String): String =
  (this as? JsonPrimitive)?.contentOrNull
    ?: throw InvalidRequestException(
      WorkbenchErrorCode.WORK_ITEM_QUERY_STRING_REQUIRED,
      "Work item query $name must be a string.",
    )

private fun JsonObject.required(key: String): JsonElement =
  this[key]
    ?: throw InvalidRequestException(
      WorkbenchErrorCode.WORK_ITEM_QUERY_FIELD_REQUIRED,
      "Work item query missing required field: $key",
    )

private fun JsonObject.requiredString(key: String): String = required(key).asString(key)

private fun JsonObject.requiredInt(key: String): Int =
  required(key).jsonPrimitive.intOrNull
    ?: throw InvalidRequestException(
      WorkbenchErrorCode.WORK_ITEM_QUERY_INTEGER_REQUIRED,
      "Work item query $key must be an integer.",
    )

private fun JsonObject.requiredArray(key: String): JsonArray = required(key).jsonArray
