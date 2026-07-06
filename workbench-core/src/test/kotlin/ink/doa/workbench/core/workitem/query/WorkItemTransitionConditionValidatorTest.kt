package ink.doa.workbench.core.workitem.query

import ink.doa.workbench.core.common.errors.InvalidRequestException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class WorkItemTransitionConditionValidatorTest :
  StringSpec({
    val validator = WorkItemTransitionConditionValidator()

    "validate accepts empty condition" {
      validator.validate(JsonObject(emptyMap()))
    }

    "validate rejects unsupported operators" {
      shouldThrow<InvalidRequestException> {
        validator.validate(
          JsonObject(
            mapOf(
              "field" to JsonPrimitive("issue.statusGroup"),
              "op" to JsonPrimitive("between"),
              "value" to JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(2))),
            )
          )
        )
      }
    }

    "validate rejects unsupported variables" {
      shouldThrow<InvalidRequestException> {
        validator.validate(
          JsonObject(
            mapOf(
              "field" to JsonPrimitive("issue.assignee"),
              "op" to JsonPrimitive("eq"),
              "value" to JsonObject(mapOf("var" to JsonPrimitive("issue.unknown"))),
            )
          )
        )
      }
    }

    "validate accepts canonical transition condition" {
      validator.validate(
        JsonObject(
          mapOf(
            "op" to JsonPrimitive("and"),
            "args" to
              kotlinx.serialization.json.JsonArray(
                listOf(
                  JsonObject(
                    mapOf(
                      "field" to JsonPrimitive("issue.assignee"),
                      "op" to JsonPrimitive("eq"),
                      "value" to JsonObject(mapOf("var" to JsonPrimitive("user.currentUser"))),
                    )
                  ),
                  JsonObject(
                    mapOf(
                      "field" to JsonPrimitive("issue.statusGroup"),
                      "op" to JsonPrimitive("eq"),
                      "value" to JsonPrimitive("todo"),
                    )
                  ),
                )
              ),
          )
        )
      )
    }
  })
