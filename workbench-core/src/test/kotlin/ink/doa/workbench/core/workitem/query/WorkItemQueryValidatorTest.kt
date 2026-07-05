package ink.doa.workbench.core.workitem.query

import ink.doa.workbench.core.common.errors.InvalidRequestException
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

    "rejects unsupported query version" {
      shouldThrow<InvalidRequestException> {
          validator.validateEnvelope(WorkItemQuery(version = 2, resource = WorkItemQuery.RESOURCE))
        }
        .message shouldBe "Unsupported work item query version: 2"
    }

    "rejects unsupported query resource" {
      shouldThrow<InvalidRequestException> {
          validator.validateEnvelope(
            WorkItemQuery(version = WorkItemQuery.CURRENT_VERSION, resource = "project")
          )
        }
        .message shouldBe "Unsupported query resource: project"
    }

    "rejects empty logical groups" {
      shouldThrow<InvalidRequestException> {
        validator.validate(WorkItemQuery(where = ConditionNode.And(emptyList())))
      }
      shouldThrow<InvalidRequestException> {
        validator.validate(WorkItemQuery(where = ConditionNode.Or(emptyList())))
      }
    }

    "accepts nested logical conditions" {
      validator.validate(
        WorkItemQuery(
          where =
            ConditionNode.And(
              listOf(
                ConditionNode.Not(
                  ConditionNode.Predicate(
                    field = QueryField.System("statusGroup"),
                    op = QueryOperator.EQ,
                    value = QueryValue.Literal(JsonPrimitive("done")),
                  )
                ),
                ConditionNode.Or(
                  listOf(
                    ConditionNode.Predicate(
                      field = QueryField.System("priority"),
                      op = QueryOperator.EQ,
                      value = QueryValue.Literal(JsonPrimitive("high")),
                    ),
                    ConditionNode.Predicate(
                      field = QueryField.System("title"),
                      op = QueryOperator.CONTAINS,
                      value = QueryValue.Literal(JsonPrimitive("bug")),
                    ),
                  )
                ),
              )
            )
        )
      )
    }

    "rejects queries that exceed nesting depth" {
      fun nested(depth: Int): ConditionNode =
        if (depth == 0) {
          ConditionNode.Predicate(
            field = QueryField.System("statusGroup"),
            op = QueryOperator.EQ,
            value = QueryValue.Literal(JsonPrimitive("todo")),
          )
        } else {
          ConditionNode.Not(nested(depth - 1))
        }

      shouldThrow<InvalidRequestException> {
        validator.validate(WorkItemQuery(where = nested(9)))
      }
    }

    "rejects queries with too many predicates" {
      val predicates =
        (1..65).map {
          ConditionNode.Predicate(
            field = QueryField.System("statusGroup"),
            op = QueryOperator.EQ,
            value = QueryValue.Literal(JsonPrimitive("todo")),
          )
        }

      shouldThrow<InvalidRequestException> {
        validator.validate(WorkItemQuery(where = ConditionNode.And(predicates)))
      }
    }

    "rejects sorting on non-sortable fields" {
      shouldThrow<InvalidRequestException> {
          validator.validate(
            WorkItemQuery(
              sort =
                listOf(
                  SortTerm(
                    field = QueryField.System("description"),
                    direction = SortDirection.ASC,
                  )
                )
            )
          )
        }
        .message shouldBe "Field description is not sortable."
    }

    "accepts sorting on sortable fields" {
      validator.validate(
        WorkItemQuery(
          sort =
            listOf(
              SortTerm(
                field = QueryField.System("createdAt"),
                direction = SortDirection.DESC,
              )
            )
        )
      )
    }

    "accepts groupable group field" {
      validator.validate(
        WorkItemQuery(group = WorkItemGroupTerm(field = QueryField.System("statusGroup")))
      )
    }

    "rejects non-groupable group field" {
      shouldThrow<InvalidRequestException> {
          validator.validate(
            WorkItemQuery(group = WorkItemGroupTerm(field = QueryField.System("title")))
          )
        }
        .message shouldBe "Field title is not groupable."
    }

    "rejects group scope without query group" {
      val key =
        ConditionNode.Predicate(
          field = QueryField.System("statusGroup"),
          op = QueryOperator.EQ,
          value = QueryValue.Literal(JsonPrimitive("todo")),
        )

      shouldThrow<InvalidRequestException> {
          validator.validate(
            WorkItemQuery(),
            WorkItemSearchGroupScope(includeGroupKeys = listOf(key)),
          )
        }
        .message shouldBe "Work item query group is required when search scope includes group keys."
    }
  })
