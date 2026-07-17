package one.ztd.workbench.agile.workitem.query

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode

class WorkItemQueryParserTest :
  StringSpec({
    val parser = WorkItemQueryParser()

    "parses canonical query json with variables and sort" {
      val query =
        parser.parse(
          """
          {
            "version": 1,
            "resource": "work_item",
            "where": {
              "op": "and",
              "args": [
                { "field": "statusGroup", "op": "in", "value": ["todo", "in_progress"] },
                { "field": "assignee", "op": "eq", "value": { "var": "user.currentUser" } }
              ]
            },
            "sort": [
              { "field": "updatedAt", "direction": "desc", "nulls": "last" }
            ]
          }
          """
            .trimIndent()
        )

      query.where shouldBe
        ConditionNode.And(
          listOf(
            ConditionNode.Predicate(
              field = QueryField.System("statusGroup"),
              op = QueryOperator.IN,
              value =
                QueryValue.Literal(
                  kotlinx.serialization.json.JsonArray(
                    listOf(
                      kotlinx.serialization.json.JsonPrimitive("todo"),
                      kotlinx.serialization.json.JsonPrimitive("in_progress"),
                    )
                  )
                ),
            ),
            ConditionNode.Predicate(
              field = QueryField.System("assignee"),
              op = QueryOperator.EQ,
              value = QueryValue.Variable("user.currentUser"),
            ),
          )
        )
      query.sort.single() shouldBe
        SortTerm(QueryField.System("updatedAt"), SortDirection.DESC, NullOrdering.LAST)
    }

    "parses object property fields and relative dates" {
      val query =
        parser.parse(
          """
          {
            "version": 1,
            "resource": "work_item",
            "where": {
              "field": { "kind": "property", "apiId": "fld_01JABC", "code": "storyPoints" },
              "op": "within",
              "value": {
                "relativeDate": {
                  "amount": 7,
                  "unit": "day",
                  "direction": "past",
                  "anchor": "date.now"
                }
              }
            }
          }
          """
            .trimIndent()
        )

      query.where shouldBe
        ConditionNode.Predicate(
          field = QueryField.Property(apiId = "fld_01JABC", code = "storyPoints"),
          op = QueryOperator.WITHIN,
          value =
            QueryValue.RelativeDate(
              amount = 7,
              unit = RelativeDateUnit.DAY,
              direction = DateDirection.PAST,
              anchor = "date.now",
            ),
        )
    }

    "parses between operands" {
      val query =
        parser.parse(
          """
          {
            "version": 1,
            "resource": "work_item",
            "where": { "field": "createdAt", "op": "between", "value": { "from": "2026-01-01" } }
          }
          """
            .trimIndent()
        )

      (query.where as ConditionNode.Predicate).value shouldBe
        QueryValue.Between(kotlinx.serialization.json.JsonPrimitive("2026-01-01"), null)
    }

    "rejects invalid envelopes" {
      shouldThrow<InvalidRequestException> {
          parser.parse("""{ "version": 2, "resource": "work_item" }""")
        }
        .message shouldBe "Unsupported work item query version: 2"
    }

    "parses or and not logical conditions" {
      val query =
        parser.parse(
          """
          {
            "version": 1,
            "resource": "work_item",
            "where": {
              "op": "or",
              "args": [
                { "field": "statusGroup", "op": "eq", "value": "todo" },
                {
                  "op": "not",
                  "arg": { "field": "assignee", "op": "is_empty" }
                }
              ]
            }
          }
          """
            .trimIndent()
        )

      query.where shouldBe
        ConditionNode.Or(
          listOf(
            ConditionNode.Predicate(
              field = QueryField.System("statusGroup"),
              op = QueryOperator.EQ,
              value = QueryValue.Literal(kotlinx.serialization.json.JsonPrimitive("todo")),
            ),
            ConditionNode.Not(
              ConditionNode.Predicate(
                field = QueryField.System("assignee"),
                op = QueryOperator.IS_EMPTY,
                value = null,
              )
            ),
          )
        )
    }

    "parses nested not inside and" {
      val query =
        parser.parse(
          """
          {
            "version": 1,
            "resource": "work_item",
            "where": {
              "op": "and",
              "args": [
                { "field": "title", "op": "contains", "value": "bug" },
                {
                  "op": "not",
                  "arg": {
                    "op": "or",
                    "args": [
                      { "field": "statusGroup", "op": "eq", "value": "done" },
                      { "field": "priority", "op": "eq", "value": "low" }
                    ]
                  }
                }
              ]
            }
          }
          """
            .trimIndent()
        )

      query.where shouldBe
        ConditionNode.And(
          listOf(
            ConditionNode.Predicate(
              field = QueryField.System("title"),
              op = QueryOperator.CONTAINS,
              value = QueryValue.Literal(kotlinx.serialization.json.JsonPrimitive("bug")),
            ),
            ConditionNode.Not(
              ConditionNode.Or(
                listOf(
                  ConditionNode.Predicate(
                    field = QueryField.System("statusGroup"),
                    op = QueryOperator.EQ,
                    value = QueryValue.Literal(kotlinx.serialization.json.JsonPrimitive("done")),
                  ),
                  ConditionNode.Predicate(
                    field = QueryField.System("priority"),
                    op = QueryOperator.EQ,
                    value = QueryValue.Literal(kotlinx.serialization.json.JsonPrimitive("low")),
                  ),
                )
              )
            ),
          )
        )
    }

    "parses property field paths by apiId and code" {
      val byApiId =
        parser.parse(
          """
          {
            "version": 1,
            "resource": "work_item",
            "where": { "field": "property.fld_01JABC", "op": "eq", "value": 1 }
          }
          """
            .trimIndent()
        )
      val byCode =
        parser.parse(
          """
          {
            "version": 1,
            "resource": "work_item",
            "where": { "field": "property.storyPoints", "op": "eq", "value": 1 }
          }
          """
            .trimIndent()
        )

      (byApiId.where as ConditionNode.Predicate).field shouldBe
        QueryField.Property(apiId = "fld_01JABC", code = null)
      (byCode.where as ConditionNode.Predicate).field shouldBe
        QueryField.Property(apiId = null, code = "storyPoints")
    }

    "parses primitive literal values" {
      val query =
        parser.parse(
          """
          {
            "version": 1,
            "resource": "work_item",
            "where": { "field": "title", "op": "eq", "value": "bug" }
          }
          """
            .trimIndent()
        )

      (query.where as ConditionNode.Predicate).value shouldBe
        QueryValue.Literal(kotlinx.serialization.json.JsonPrimitive("bug"))
    }

    "rejects invalid json payloads" {
      shouldThrow<InvalidRequestException> {
          parser.parse("""{ "version": 1, "resource": "work_item", """)
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_QUERY_INVALID_JSON
    }

    "rejects property fields without an identity as a domain error" {
      shouldThrow<InvalidRequestException> {
          parser.parse(
            """
            {
              "version": 1,
              "resource": "work_item",
              "where": { "field": { "kind": "property" }, "op": "eq", "value": "bug" }
            }
            """
              .trimIndent()
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_QUERY_PROPERTY_IDENTITY_REQUIRED
    }

    "rejects non-array logical arguments as a domain error" {
      shouldThrow<InvalidRequestException> {
          parser.parse(
            """
            {
              "version": 1,
              "resource": "work_item",
              "where": { "op": "and", "args": "not-an-array" }
            }
            """
              .trimIndent()
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_QUERY_ARRAY_REQUIRED
    }

    "rejects unknown logical operators and sort metadata" {
      shouldThrow<InvalidRequestException> {
          parser.parse(
            """
            {
              "version": 1,
              "resource": "work_item",
              "where": { "op": "xor", "args": [] }
            }
            """
              .trimIndent()
          )
        }
        .message shouldBe "Unknown work item query logical operator: xor"

      shouldThrow<InvalidRequestException> {
          parser.parse(
            """
            {
              "version": 1,
              "resource": "work_item",
              "sort": [{ "field": "title", "direction": "sideways" }]
            }
            """
              .trimIndent()
          )
        }
        .message shouldBe "Unknown work item sort direction: sideways"

      shouldThrow<InvalidRequestException> {
          parser.parse(
            """
            {
              "version": 1,
              "resource": "work_item",
              "sort": [{ "field": "title", "direction": "asc", "nulls": "middle" }]
            }
            """
              .trimIndent()
          )
        }
        .message shouldBe "Unknown work item sort null ordering: middle"
    }

    "rejects invalid field shapes and kinds" {
      shouldThrow<InvalidRequestException> {
          parser.parse(
            """
            {
              "version": 1,
              "resource": "work_item",
              "where": { "field": { "kind": "system", "name": "title" }, "op": "eq", "value": "x" }
            }
            """
              .trimIndent()
          )
        }
        .message shouldBe "Unknown work item query field kind: system"

      shouldThrow<InvalidRequestException> {
          parser.parse(
            """
            {
              "version": 1,
              "resource": "work_item",
              "where": { "field": ["title"], "op": "eq", "value": "x" }
            }
            """
              .trimIndent()
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_QUERY_FIELD_SHAPE_INVALID

      shouldThrow<InvalidRequestException> {
          parser.parse(
            """
            {
              "version": 1,
              "resource": "work_item",
              "where": { "field": "property.", "op": "eq", "value": "x" }
            }
            """
              .trimIndent()
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_QUERY_PROPERTY_IDENTITY_REQUIRED
    }

    "rejects unknown operators and relative date metadata" {
      shouldThrow<InvalidRequestException> {
          parser.parse(
            """
            {
              "version": 1,
              "resource": "work_item",
              "where": { "field": "title", "op": "like", "value": "bug" }
            }
            """
              .trimIndent()
          )
        }
        .message shouldBe "Unknown work item query operator: like"

      shouldThrow<InvalidRequestException> {
          parser.parse(
            """
            {
              "version": 1,
              "resource": "work_item",
              "where": {
                "field": "createdAt",
                "op": "within",
                "value": {
                  "relativeDate": {
                    "amount": 1,
                    "unit": "fortnight",
                    "direction": "past",
                    "anchor": "date.now"
                  }
                }
              }
            }
            """
              .trimIndent()
          )
        }
        .message shouldBe "Unknown relative date unit: fortnight"

      shouldThrow<InvalidRequestException> {
          parser.parse(
            """
            {
              "version": 1,
              "resource": "work_item",
              "where": {
                "field": "createdAt",
                "op": "within",
                "value": {
                  "relativeDate": {
                    "amount": 1,
                    "unit": "day",
                    "direction": "sideways",
                    "anchor": "date.now"
                  }
                }
              }
            }
            """
              .trimIndent()
          )
        }
        .message shouldBe "Unknown relative date direction: sideways"
    }

    "rejects malformed query structure" {
      shouldThrow<InvalidRequestException> {
          parser.parse("""{ "version": 1, "resource": "work_item", "where": "title" }""")
        }
        .message shouldBe "Work item query condition must be an object."

      shouldThrow<InvalidRequestException> {
          parser.parse("""{ "version": 1, "resource": "work_item", "sort": "title" }""")
        }
        .message shouldBe "Work item query sort must be an array."

      shouldThrow<InvalidRequestException> {
          parser.parse(
            """
            {
              "version": "one",
              "resource": "work_item"
            }
            """
              .trimIndent()
          )
        }
        .message shouldBe "Work item query version must be an integer."

      shouldThrow<InvalidRequestException> {
          parser.parse(
            """{ "version": 1, "resource": "work_item" }""".replace("\"resource\"", "\"missing\"")
          )
        }
        .message shouldBe "Work item query missing required field: resource"
    }

    "parses group term and group scope" {
      val query =
        parser.parse(
          """
          {
            "version": 1,
            "resource": "work_item",
            "group": { "field": "statusGroup", "direction": "asc" }
          }
          """
            .trimIndent()
        )

      query.group shouldBe
        WorkItemGroupTerm(field = QueryField.System("statusGroup"), direction = SortDirection.ASC)

      val scope =
        parser.parseGroupScope(
          Json.parseToJsonElement(
            """
            {
              "includeGroupKeys": [
                { "field": "statusGroup", "op": "eq", "value": "todo" }
              ]
            }
            """
              .trimIndent()
          )
        )

      scope.includeGroupKeys.single().op shouldBe QueryOperator.EQ
    }
  })
