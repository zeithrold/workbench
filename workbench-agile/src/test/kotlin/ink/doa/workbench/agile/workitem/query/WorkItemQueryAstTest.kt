package ink.doa.workbench.agile.workitem.query

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonPrimitive

class WorkItemQueryAstTest :
  StringSpec({
    "query operator resolves wire names" {
      QueryOperator.fromWireName("eq") shouldBe QueryOperator.EQ
      QueryOperator.fromWireName("has_any") shouldBe QueryOperator.HAS_ANY
      QueryOperator.fromWireName("unknown") shouldBe null
    }

    "property field requires api id or code" {
      shouldThrow<IllegalArgumentException> { QueryField.Property(apiId = null, code = null) }
      QueryField.Property(apiId = "fld_points", code = null).canonicalName shouldBe
        "property.fld_points"
      QueryField.Property(apiId = null, code = "points").canonicalName shouldBe "property.points"
    }

    "condition nodes compose nested predicates" {
      val query =
        WorkItemQuery(
          where =
            ConditionNode.And(
              listOf(
                ConditionNode.Predicate(
                  field = QueryField.System("status"),
                  op = QueryOperator.EQ,
                  value = QueryValue.Literal(JsonPrimitive("todo")),
                ),
                ConditionNode.Not(
                  ConditionNode.Predicate(
                    field = QueryField.Property(code = "priority", apiId = null),
                    op = QueryOperator.IS_EMPTY,
                  )
                ),
              )
            ),
          sort =
            listOf(
              SortTerm(field = QueryField.System("created_at"), direction = SortDirection.DESC)
            ),
        )

      query.resource shouldBe WorkItemQuery.RESOURCE
      (query.where as ConditionNode.And).args.size shouldBe 2
      query.sort.single().direction shouldBe SortDirection.DESC
    }
  })
