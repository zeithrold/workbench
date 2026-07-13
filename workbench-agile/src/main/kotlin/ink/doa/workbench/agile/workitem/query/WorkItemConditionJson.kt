package ink.doa.workbench.agile.workitem.query

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object WorkItemConditionJson {
  private val parser = WorkItemQueryParser(Json { ignoreUnknownKeys = true })

  fun parse(element: JsonObject): ConditionNode? {
    if (element.isEmpty()) return null
    WorkItemConditionSyntax.validate(element)
    return parser.parseCondition(element)
  }

  fun canonicalize(element: JsonObject): JsonObject =
    when {
      element.isEmpty() -> element
      else -> parse(element)?.toJsonObject() ?: JsonObject(emptyMap())
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
            "field" to field.toJsonElement(),
            "op" to JsonPrimitive(op.wireName),
          )
        value?.let { fields["value"] = it.toJsonElement() }
        JsonObject(fields)
      }
    }

  private fun QueryField.toJsonElement(): JsonElement =
    when (this) {
      is QueryField.System -> JsonPrimitive(canonicalName)
      is QueryField.Property ->
        JsonObject(
          buildMap {
            put("kind", JsonPrimitive("property"))
            apiId?.let { put("apiId", JsonPrimitive(it)) }
            code?.let { put("code", JsonPrimitive(it)) }
          }
        )
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
}
