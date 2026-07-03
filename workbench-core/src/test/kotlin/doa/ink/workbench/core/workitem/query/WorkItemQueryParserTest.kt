package doa.ink.workbench.core.workitem.query

import doa.ink.workbench.core.common.errors.InvalidRequestException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

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
  })
