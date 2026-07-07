package ink.doa.workbench.core.workitem.query

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import io.kotest.assertions.throwables.shouldThrow
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

    "parse accepts canonical and composition" {
      val canonical =
        json
          .parseToJsonElement(
            """
            {
              "op": "and",
              "args": [
                { "field": "issue.statusGroup", "op": "eq", "value": "todo" },
                { "field": "issue.assignee", "op": "eq", "value": { "var": "user.currentUser" } }
              ]
            }
            """
              .trimIndent()
          )
          .jsonObject

      val parsed = WorkItemConditionJson.parse(canonical)

      parsed shouldBe
        ConditionNode.And(
          listOf(
            ConditionNode.Predicate(
              field = QueryField.System("issue.statusGroup"),
              op = QueryOperator.EQ,
              value = QueryValue.Literal(JsonPrimitive("todo")),
            ),
            ConditionNode.Predicate(
              field = QueryField.System("issue.assignee"),
              op = QueryOperator.EQ,
              value = QueryValue.Variable("user.currentUser"),
            ),
          )
        )
    }

    "parse rejects legacy all syntax" {
      val legacy =
        json
          .parseToJsonElement(
            """
            {
              "all": [
                { "field": "issue.statusGroup", "op": "eq", "value": "todo" }
              ]
            }
            """
              .trimIndent()
          )
          .jsonObject

      shouldThrow<InvalidRequestException> { WorkItemConditionJson.parse(legacy) }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_CONDITION_LEGACY_SYNTAX
    }

    "parse rejects legacy field aliases" {
      val legacy =
        json
          .parseToJsonElement(
            """{ "field": "assignee", "op": "eq", "value": { "var": "user.currentUser" } }"""
              .trimIndent()
          )
          .jsonObject

      shouldThrow<InvalidRequestException> { WorkItemConditionJson.parse(legacy) }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_CONDITION_LEGACY_SYNTAX
    }

    "parse rejects legacy operators" {
      val legacy =
        json
          .parseToJsonElement(
            """{ "field": "issue.statusGroup", "op": "==", "value": "todo" }""".trimIndent()
          )
          .jsonObject

      shouldThrow<InvalidRequestException> { WorkItemConditionJson.parse(legacy) }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_CONDITION_LEGACY_SYNTAX
    }

    "parse rejects uuid literals for entity identifiers" {
      val condition =
        json
          .parseToJsonElement(
            """
            {
              "field": "issue.assignee",
              "op": "eq",
              "value": "550e8400-e29b-41d4-a716-446655440000"
            }
            """
              .trimIndent()
          )
          .jsonObject

      shouldThrow<InvalidRequestException> { WorkItemConditionJson.parse(condition) }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_CONDITION_UUID_LITERAL_FORBIDDEN
    }

    "canonicalize round-trips canonical predicates" {
      val canonical =
        json
          .parseToJsonElement(
            """
            {
              "field": "issue.statusGroup",
              "op": "in",
              "value": ["todo"]
            }
            """
              .trimIndent()
          )
          .jsonObject

      WorkItemConditionJson.canonicalize(canonical) shouldBe canonical
    }

    "canonicalize returns empty object when parse yields null" {
      WorkItemConditionJson.canonicalize(JsonObject(emptyMap())) shouldBe JsonObject(emptyMap())
    }

    "canonicalize round-trips relative date values" {
      val input =
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

      WorkItemConditionJson.canonicalize(input) shouldBe
        JsonObject(
          mapOf(
            "field" to
              JsonObject(
                mapOf(
                  "kind" to JsonPrimitive("property"),
                  "code" to JsonPrimitive("dueDate"),
                )
              ),
            "op" to JsonPrimitive("eq"),
            "value" to
              JsonObject(
                mapOf(
                  "relativeDate" to
                    JsonObject(
                      mapOf(
                        "amount" to JsonPrimitive(2),
                        "unit" to JsonPrimitive("day"),
                        "direction" to JsonPrimitive("future"),
                        "anchor" to JsonPrimitive("date.today"),
                      )
                    )
                )
              ),
          )
        )
    }

    "parse accepts apiId literals in in arrays" {
      val condition =
        JsonObject(
          mapOf(
            "field" to JsonPrimitive("issue.assignee"),
            "op" to JsonPrimitive("in"),
            "value" to
              JsonArray(
                listOf(
                  JsonObject(mapOf("var" to JsonPrimitive("user.currentUser"))),
                  JsonPrimitive("usr_01JABCDEFGHJKMNPQRSTVWXYZ0"),
                )
              ),
          )
        )

      WorkItemConditionJson.parse(condition) shouldBe
        ConditionNode.Predicate(
          field = QueryField.System("issue.assignee"),
          op = QueryOperator.IN,
          value =
            QueryValue.Literal(
              JsonArray(
                listOf(
                  JsonObject(mapOf("var" to JsonPrimitive("user.currentUser"))),
                  JsonPrimitive("usr_01JABCDEFGHJKMNPQRSTVWXYZ0"),
                )
              )
            ),
        )
    }
  })
