package doa.ink.workbench.workitem.query

import doa.ink.workbench.shared.errors.InvalidRequestException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class WorkItemQueryValidatorTest :
  StringSpec({
    val validator = WorkItemQueryValidator()

    "accepts a simple status group predicate" {
      validator.validate(
        WorkItemQuery(
          where =
            ConditionNode.Predicate(
              field = "statusGroup",
              op = "eq",
              value = QueryValue.Literal("todo"),
            )
        )
      )
    }

    "rejects unknown fields" {
      shouldThrow<InvalidRequestException> {
          validator.validate(
            WorkItemQuery(where = ConditionNode.Predicate(field = "internalSecret", op = "eq"))
          )
        }
        .message shouldBe "Unknown work item query field: internalSecret"
    }
  })
