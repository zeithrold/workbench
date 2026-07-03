package doa.ink.workbench.core.workitem.query

import doa.ink.workbench.core.common.errors.InvalidRequestException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

class WorkItemQueryValidatorTest :
  StringSpec({
    val validator = WorkItemQueryValidator()

    "accepts a simple status group predicate" {
      validator.validate(
        WorkItemQuery(
          where =
            ConditionNode.Predicate(
              field = QueryField.System("statusGroup"),
              op = QueryOperator.EQ,
              value = QueryValue.Literal(JsonPrimitive("todo")),
            )
        )
      )
    }

    "rejects unknown fields" {
      shouldThrow<InvalidRequestException> {
          validator.validate(
            WorkItemQuery(
              where =
                ConditionNode.Predicate(
                  field = QueryField.System("internalSecret"),
                  op = QueryOperator.EQ,
                  value = QueryValue.Literal(JsonPrimitive("x")),
                )
            )
          )
        }
        .message shouldBe "Unknown work item query field: internalSecret"
    }

    "rejects empty arrays for membership operators" {
      shouldThrow<InvalidRequestException> {
          validator.validate(
            WorkItemQuery(
              where =
                ConditionNode.Predicate(
                  field = QueryField.System("status"),
                  op = QueryOperator.IN,
                  value = QueryValue.Literal(JsonArray(emptyList())),
                )
            )
          )
        }
        .message shouldBe "Operator in requires a non-empty array value."
    }

    "rejects values on unary operators" {
      shouldThrow<InvalidRequestException> {
          validator.validate(
            WorkItemQuery(
              where =
                ConditionNode.Predicate(
                  field = QueryField.System("assignee"),
                  op = QueryOperator.IS_EMPTY,
                  value = QueryValue.Literal(JsonPrimitive("usr_1")),
                )
            )
          )
        }
        .message shouldBe "Operator is_empty does not accept a value."
    }

    "rejects operators unsupported by the field type" {
      shouldThrow<InvalidRequestException> {
          validator.validate(
            WorkItemQuery(
              where =
                ConditionNode.Predicate(
                  field = QueryField.System("assignee"),
                  op = QueryOperator.CONTAINS,
                  value = QueryValue.Literal(JsonPrimitive("usr_1")),
                )
            )
          )
        }
        .message shouldBe "Operator contains is not supported by field assignee."
    }
  })
