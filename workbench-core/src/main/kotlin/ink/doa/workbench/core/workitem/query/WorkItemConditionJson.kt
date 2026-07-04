package ink.doa.workbench.core.workitem.query

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object WorkItemConditionJson {
  private val parser = WorkItemQueryParser()
  private val legacyVariables = setOf("user.currentUser", "issue.reporter", "issue.assignee")

  fun parse(element: JsonObject): ConditionNode? {
    if (element.isEmpty()) return null
    return parser.parseCondition(toCanonicalElement(element))
  }

  fun canonicalize(element: JsonObject): JsonObject {
    if (element.isEmpty()) return element
    val condition = parse(element) ?: return JsonObject(emptyMap())
    return condition.toJsonObject()
  }

  private fun toCanonicalElement(element: JsonElement): JsonElement {
    val obj = element as? JsonObject ?: return element
    if (!obj.isLegacyCondition()) return obj
    return when {
      "all" in obj ->
        JsonObject(
          mapOf(
            "op" to JsonPrimitive("and"),
            "args" to JsonArray(obj.array("all").map(::toCanonicalElement)),
          )
        )
      "any" in obj ->
        JsonObject(
          mapOf(
            "op" to JsonPrimitive("or"),
            "args" to JsonArray(obj.array("any").map(::toCanonicalElement)),
          )
        )
      "not" in obj ->
        JsonObject(
          mapOf(
            "op" to JsonPrimitive("not"),
            "arg" to toCanonicalElement(obj.getValue("not")),
          )
        )
      else -> canonicalPredicate(obj)
    }
  }

  private fun canonicalPredicate(obj: JsonObject): JsonObject {
    val op = obj.getValue("op").jsonPrimitive.content
    val canonicalOp =
      when (op) {
        "==" -> "eq"
        "ne",
        "!=" -> "neq"
        "exists" -> "is_not_empty"
        "missing" -> "is_empty"
        else -> op
      }
    val fields =
      linkedMapOf<String, JsonElement>(
        "field" to obj.getValue("field"),
        "op" to JsonPrimitive(canonicalOp),
      )
    if (canonicalOp !in setOf("is_empty", "is_not_empty")) {
      fields["value"] = toCanonicalValue(obj["value"] ?: JsonNull)
    }
    return JsonObject(fields)
  }

  private fun toCanonicalValue(value: JsonElement): JsonElement =
    when (value) {
      is JsonPrimitive ->
        if (value.contentOrNull in legacyVariables) {
          JsonObject(mapOf("var" to JsonPrimitive(value.content)))
        } else {
          value
        }
      is JsonArray -> JsonArray(value.map(::toCanonicalValue))
      else -> value
    }

  private fun ConditionNode.toJsonObject(): JsonObject =
    when (this) {
      is ConditionNode.And ->
        JsonObject(
          mapOf(
            "op" to JsonPrimitive("and"),
            "args" to JsonArray(args.map { it.toJsonObject() }),
          )
        )
      is ConditionNode.Or ->
        JsonObject(
          mapOf(
            "op" to JsonPrimitive("or"),
            "args" to JsonArray(args.map { it.toJsonObject() }),
          )
        )
      is ConditionNode.Not ->
        JsonObject(mapOf("op" to JsonPrimitive("not"), "arg" to arg.toJsonObject()))
      is ConditionNode.Predicate -> {
        val fields =
          linkedMapOf<String, JsonElement>(
            "field" to JsonPrimitive(field.canonicalName),
            "op" to JsonPrimitive(op.wireName),
          )
        value?.let { fields["value"] = it.toJsonElement() }
        JsonObject(fields)
      }
    }

  private fun QueryValue.toJsonElement(): JsonElement =
    when (this) {
      is QueryValue.Literal -> value
      is QueryValue.Variable -> JsonObject(mapOf("var" to JsonPrimitive(name)))
      is QueryValue.RelativeDate ->
        JsonObject(
          mapOf(
            "relativeDate" to
              JsonObject(
                mapOf(
                  "amount" to JsonPrimitive(amount),
                  "unit" to JsonPrimitive(unit.wireName),
                  "direction" to JsonPrimitive(direction.wireName),
                  "anchor" to JsonPrimitive(anchor),
                )
              )
          )
        )
      is QueryValue.Between ->
        JsonObject(mapOf("from" to (from ?: JsonNull), "to" to (to ?: JsonNull)))
    }

  private fun JsonObject.isLegacyCondition(): Boolean =
    "all" in this || "any" in this || ("not" in this && this["op"] == null) || legacyPredicate()

  private fun JsonObject.legacyPredicate(): Boolean {
    if ("field" !in this) return false
    val op = this["op"]?.jsonPrimitive?.contentOrNull ?: return false
    val value = this["value"]
    return op in setOf("ne", "==", "!=", "exists", "missing") ||
      (value is JsonPrimitive && value.contentOrNull in legacyVariables) ||
      (value is JsonArray && value.any { it is JsonPrimitive && it.contentOrNull in legacyVariables })
  }

  private fun JsonObject.array(name: String): JsonArray = getValue(name) as JsonArray
}
