package ink.doa.workbench.core.workitem.query

import ink.doa.workbench.core.common.errors.InvalidRequestException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

class WorkItemQueryValueValidatorsTest :
  StringSpec({
    "accepts null value for unary operators" {
      WorkItemQueryValueValidators.validateValueShape(
        WorkItemQueryFieldType.USER,
        QueryOperator.IS_EMPTY,
        null,
      )
    }

    "rejects value on unary operators" {
      shouldThrow<InvalidRequestException> {
          WorkItemQueryValueValidators.validateValueShape(
            WorkItemQueryFieldType.USER,
            QueryOperator.IS_NOT_EMPTY,
            QueryValue.Literal(JsonPrimitive("usr_1")),
          )
        }
        .message shouldBe "Operator is_not_empty does not accept a value."
    }

    "rejects missing value on binary operators" {
      shouldThrow<InvalidRequestException> {
          WorkItemQueryValueValidators.validateValueShape(
            WorkItemQueryFieldType.TEXT,
            QueryOperator.EQ,
            null,
          )
        }
        .message shouldBe "Operator eq requires a value."
    }

    "rejects empty array for membership operators" {
      shouldThrow<InvalidRequestException> {
          WorkItemQueryValueValidators.validateValueShape(
            WorkItemQueryFieldType.TEXT,
            QueryOperator.IN,
            QueryValue.Literal(JsonArray(emptyList())),
          )
        }
        .message shouldBe "Operator in requires a non-empty array value."
    }

    "rejects non-array for membership operators" {
      shouldThrow<InvalidRequestException> {
          WorkItemQueryValueValidators.validateValueShape(
            WorkItemQueryFieldType.TEXT,
            QueryOperator.HAS_ANY,
            QueryValue.Literal(JsonPrimitive("x")),
          )
        }
        .message shouldBe "Operator has_any requires an array value."
    }

    "requires at least one bound for between" {
      shouldThrow<InvalidRequestException> {
        WorkItemQueryValueValidators.validateValueShape(
          WorkItemQueryFieldType.NUMBER,
          QueryOperator.BETWEEN,
          QueryValue.Between(from = null, to = null),
        )
      }
    }

    "accepts between with from bound only" {
      WorkItemQueryValueValidators.validateValueShape(
        WorkItemQueryFieldType.NUMBER,
        QueryOperator.BETWEEN,
        QueryValue.Between(from = JsonPrimitive(1), to = null),
      )
    }

    "rejects relative date with non-positive amount" {
      shouldThrow<InvalidRequestException> {
        WorkItemQueryValueValidators.validateValueShape(
          WorkItemQueryFieldType.DATETIME,
          QueryOperator.WITHIN,
          QueryValue.RelativeDate(
            amount = 0,
            unit = RelativeDateUnit.DAY,
            direction = DateDirection.PAST,
            anchor = "date.now",
          ),
        )
      }
    }

    "rejects relative date with unknown anchor" {
      shouldThrow<InvalidRequestException> {
          WorkItemQueryValueValidators.validateValueShape(
            WorkItemQueryFieldType.DATETIME,
            QueryOperator.WITHIN,
            QueryValue.RelativeDate(
              amount = 7,
              unit = RelativeDateUnit.DAY,
              direction = DateDirection.PAST,
              anchor = "date.unknown",
            ),
          )
        }
        .message shouldBe "Unknown relative date anchor: date.unknown"
    }

    "requires string for text-like operators on scalar fields" {
      shouldThrow<InvalidRequestException> {
          WorkItemQueryValueValidators.validateValueShape(
            WorkItemQueryFieldType.TEXT,
            QueryOperator.CONTAINS,
            QueryValue.Literal(JsonPrimitive(42)),
          )
        }
        .message shouldBe "Operator contains requires a string value."
    }

    "accepts variable for eq on user fields" {
      WorkItemQueryValueValidators.validateValueShape(
        WorkItemQueryFieldType.USER,
        QueryOperator.EQ,
        QueryValue.Variable("user.currentUser"),
      )
    }

    "rejects unknown variables" {
      shouldThrow<InvalidRequestException> {
          WorkItemQueryValueValidators.validateValueShape(
            WorkItemQueryFieldType.USER,
            QueryOperator.EQ,
            QueryValue.Variable("user.unknown"),
          )
        }
        .message shouldBe "Unknown work item query variable: user.unknown"
    }

    "rejects null literal for scalar operators" {
      shouldThrow<InvalidRequestException> {
          WorkItemQueryValueValidators.validateValueShape(
            WorkItemQueryFieldType.TEXT,
            QueryOperator.EQ,
            QueryValue.Literal(JsonNull),
          )
        }
        .message shouldBe "Operator eq requires a single value."
    }

    "accepts starts_with with string value" {
      WorkItemQueryValueValidators.validateValueShape(
        WorkItemQueryFieldType.TEXT,
        QueryOperator.STARTS_WITH,
        QueryValue.Literal(JsonPrimitive("bug")),
      )
    }

    "accepts between with to bound only" {
      WorkItemQueryValueValidators.validateValueShape(
        WorkItemQueryFieldType.NUMBER,
        QueryOperator.BETWEEN,
        QueryValue.Between(from = null, to = JsonPrimitive(10)),
      )
    }

    "accepts project current project variable" {
      WorkItemQueryValueValidators.validateValueShape(
        WorkItemQueryFieldType.PROJECT,
        QueryOperator.EQ,
        QueryValue.Variable("project.currentProject"),
      )
    }

    "accepts relative date with date.today anchor" {
      WorkItemQueryValueValidators.validateValueShape(
        WorkItemQueryFieldType.DATE,
        QueryOperator.WITHIN,
        QueryValue.RelativeDate(
          amount = 3,
          unit = RelativeDateUnit.DAY,
          direction = DateDirection.FUTURE,
          anchor = "date.today",
        ),
      )
    }
  })
