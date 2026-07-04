package ink.doa.workbench.core.workitem.query

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class WorkItemConditionJsonTest :
  StringSpec({
    val json = Json { ignoreUnknownKeys = true }

    "parse returns null for empty object" {
      WorkItemConditionJson.parse(JsonObject(emptyMap())).shouldBeNull()
    }

    "parse converts legacy all to canonical and" {
      val legacy =
        json
          .parseToJsonElement(
            """
            {
              "all": [
                { "field": "statusGroup", "op": "==", "value": "todo" },
                { "field": "assignee", "op": "eq", "value": "user.currentUser" }
              ]
            }
            """
              .trimIndent()
          )
          .jsonObject

      val parsed = WorkItemConditionJson.parse(legacy)

      parsed shouldBe
        ConditionNode.And(
          listOf(
            ConditionNode.Predicate(
              field = QueryField.System("statusGroup"),
              op = QueryOperator.EQ,
              value = QueryValue.Literal(JsonPrimitive("todo")),
            ),
            ConditionNode.Predicate(
              field = QueryField.System("assignee"),
              op = QueryOperator.EQ,
              value = QueryValue.Variable("user.currentUser"),
            ),
          )
        )
    }

    "parse converts legacy any to canonical or" {
      val legacy =
        json
          .parseToJsonElement(
            """
            {
              "any": [
                { "field": "statusGroup", "op": "ne", "value": "done" },
                { "field": "assignee", "op": "missing" }
              ]
            }
            """
              .trimIndent()
          )
          .jsonObject

      val parsed = WorkItemConditionJson.parse(legacy)

      parsed shouldBe
        ConditionNode.Or(
          listOf(
            ConditionNode.Predicate(
              field = QueryField.System("statusGroup"),
              op = QueryOperator.NEQ,
              value = QueryValue.Literal(JsonPrimitive("done")),
            ),
            ConditionNode.Predicate(
              field = QueryField.System("assignee"),
              op = QueryOperator.IS_EMPTY,
              value = null,
            ),
          )
        )
    }

    "parse converts legacy not and exists operators" {
      val legacy =
        json
          .parseToJsonElement(
            """
            {
              "not": {
                "field": "assignee",
                "op": "exists"
              }
            }
            """
              .trimIndent()
          )
          .jsonObject

      val parsed = WorkItemConditionJson.parse(legacy)

      parsed shouldBe
        ConditionNode.Not(
          ConditionNode.Predicate(
            field = QueryField.System("assignee"),
            op = QueryOperator.IS_NOT_EMPTY,
            value = null,
          )
        )
    }

    "canonicalize round-trips canonical condition" {
      val canonical =
        json
          .parseToJsonElement(
            """
            {
              "op": "and",
              "args": [
                { "field": "statusGroup", "op": "in", "value": ["todo"] },
                { "field": "assignee", "op": "eq", "value": { "var": "user.currentUser" } }
              ]
            }
            """
              .trimIndent()
          )
          .jsonObject

      WorkItemConditionJson.canonicalize(canonical) shouldBe canonical
    }

    "canonicalize returns empty object when parse yields null" {
      val emptyCondition = JsonObject(emptyMap())

      WorkItemConditionJson.canonicalize(emptyCondition) shouldBe JsonObject(emptyMap())
    }

    "parse converts legacy equality and inequality operators" {
      val legacy =
        json
          .parseToJsonElement(
            """
            { "field": "statusGroup", "op": "==", "value": "todo" }
            """
              .trimIndent()
          )
          .jsonObject

      val parsed = WorkItemConditionJson.parse(legacy)

      parsed shouldBe
        ConditionNode.Predicate(
          field = QueryField.System("statusGroup"),
          op = QueryOperator.EQ,
          value = QueryValue.Literal(JsonPrimitive("todo")),
        )
    }

    "parse converts legacy variable primitive to canonical variable object" {
      val legacy =
        JsonObject(
          mapOf(
            "field" to JsonPrimitive("assignee"),
            "op" to JsonPrimitive("eq"),
            "value" to JsonPrimitive("user.currentUser"),
          )
        )

      val parsed = WorkItemConditionJson.parse(legacy)

      parsed shouldBe
        ConditionNode.Predicate(
          field = QueryField.System("assignee"),
          op = QueryOperator.EQ,
          value = QueryValue.Variable("user.currentUser"),
        )
    }

    "parse handles legacy variable in array values" {
      val legacy =
        JsonObject(
          mapOf(
            "field" to JsonPrimitive("assignee"),
            "op" to JsonPrimitive("in"),
            "value" to JsonArray(listOf(JsonPrimitive("user.currentUser"), JsonPrimitive("usr_1"))),
          )
        )

      val parsed = WorkItemConditionJson.parse(legacy)

      parsed shouldBe
        ConditionNode.Predicate(
          field = QueryField.System("assignee"),
          op = QueryOperator.IN,
          value =
            QueryValue.Literal(
              JsonArray(
                listOf(
                  JsonObject(mapOf("var" to JsonPrimitive("user.currentUser"))),
                  JsonPrimitive("usr_1"),
                )
              )
            ),
        )
    }

    "parse converts legacy inequality operator" {
      val legacy =
        JsonObject(
          mapOf(
            "field" to JsonPrimitive("statusGroup"),
            "op" to JsonPrimitive("!="),
            "value" to JsonPrimitive("done"),
          )
        )

      val parsed = WorkItemConditionJson.parse(legacy)

      parsed shouldBe
        ConditionNode.Predicate(
          field = QueryField.System("statusGroup"),
          op = QueryOperator.NEQ,
          value = QueryValue.Literal(JsonPrimitive("done")),
        )
    }

    "parse converts legacy missing operator" {
      val legacy =
        JsonObject(
          mapOf(
            "field" to JsonPrimitive("assignee"),
            "op" to JsonPrimitive("missing"),
          )
        )

      val parsed = WorkItemConditionJson.parse(legacy)

      parsed shouldBe
        ConditionNode.Predicate(
          field = QueryField.System("assignee"),
          op = QueryOperator.IS_EMPTY,
          value = null,
        )
    }

    "canonicalize round-trips relative date values" {
      val canonical =
        json
          .parseToJsonElement(
            """
            {
              "field": "property.dueDate",
              "op": "eq",
              "value": {
                "relativeDate": {
                  "amount": 2,
                  "unit": "day",
                  "direction": "future",
                  "anchor": "date.today"
                }
              }
            }
            """
              .trimIndent()
          )
          .jsonObject

      WorkItemConditionJson.canonicalize(canonical) shouldBe canonical
    }
  })
