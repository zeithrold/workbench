package one.ztd.workbench.agile.workitem.query

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.SerializationParseSupport
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode

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
        group = obj["group"]?.let(::parseGroup),
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

  fun parseSortTerms(element: JsonElement): List<SortTerm> = parseSort(element)

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

  fun parseField(element: JsonElement): QueryField = parseFieldInternal(element)

  fun parseGroupKey(element: JsonElement): WorkItemGroupKey {
    val predicate = parseCondition(element)
    if (predicate !is ConditionNode.Predicate) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_QUERY_GROUP_KEY_OPERATOR_UNSUPPORTED,
        "Work item group key must be a predicate.",
      )
    }
    return predicate
  }

  fun parseGroupScope(element: JsonElement): WorkItemSearchGroupScope {
    val obj = element.asObject("scope")
    return WorkItemSearchGroupScope(
      includeGroupKeys =
        obj["includeGroupKeys"]?.asArray("includeGroupKeys")?.map(::parseGroupKey).orEmpty(),
      excludeGroupKeys =
        obj["excludeGroupKeys"]?.asArray("excludeGroupKeys")?.map(::parseGroupKey).orEmpty(),
    )
  }

  private fun parseGroup(element: JsonElement): WorkItemGroupTerm {
    val obj = element.asObject("group")
    val direction =
      obj["direction"]?.let {
        SortDirection.fromWireName(it.asString("group direction"))
          ?: throw InvalidRequestException(
            WorkbenchErrorCode.WORK_ITEM_QUERY_SORT_DIRECTION_UNKNOWN,
            "Unknown work item group direction: ${it.asString("group direction")}",
          )
      } ?: SortDirection.ASC
    return WorkItemGroupTerm(field = parseField(obj.required("field")), direction = direction)
  }

  private fun parseFieldInternal(element: JsonElement): QueryField =
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
        val apiId = element["apiId"]?.asString("property apiId")
        val code = element["code"]?.asString("property code")
        if (apiId.isNullOrBlank() && code.isNullOrBlank()) {
          throw InvalidRequestException(
            WorkbenchErrorCode.WORK_ITEM_QUERY_PROPERTY_IDENTITY_REQUIRED
          )
        }
        QueryField.Property(
          apiId = apiId,
          code = code,
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
  (required(key) as? JsonPrimitive)?.intOrNull
    ?: throw InvalidRequestException(
      WorkbenchErrorCode.WORK_ITEM_QUERY_INTEGER_REQUIRED,
      "Work item query $key must be an integer.",
    )

private fun JsonObject.requiredArray(key: String): JsonArray = required(key).asArray(key)
