package ink.doa.workbench.data.workitem

import ink.doa.workbench.core.workitem.WorkItemSearchScope
import ink.doa.workbench.core.workitem.query.ConditionNode
import ink.doa.workbench.core.workitem.query.DateDirection
import ink.doa.workbench.core.workitem.query.QueryField
import ink.doa.workbench.core.workitem.query.QueryOperator
import ink.doa.workbench.core.workitem.query.QueryValue
import ink.doa.workbench.core.workitem.query.RelativeDateUnit
import ink.doa.workbench.core.workitem.query.SortDirection
import ink.doa.workbench.core.workitem.query.SortTerm
import ink.doa.workbench.core.workitem.query.WorkItemQuery
import ink.doa.workbench.core.workitem.query.WorkItemQueryFieldType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

class PostgresWorkItemFilterTest :
  StringSpec({
    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()
    val resolver =
      StaticPostgresWorkItemFieldResolver(
        mapOf(
          "storyPoints" to WorkItemQueryFieldType.NUMBER,
          "targetDate" to WorkItemQueryFieldType.DATE,
          "customerImpact" to WorkItemQueryFieldType.MULTI_SELECT,
        )
      )
    val filter = PostgresWorkItemFilter(resolver)

    "always applies trusted tenant project and lifecycle scope" {
      val plan =
        filter.build(
          WorkItemSearchScope(tenantId = tenantId, projectId = projectId),
          WorkItemQuery(),
        )

      plan.where.sql shouldContain "i.tenant_id = ?"
      plan.where.sql shouldContain "i.project_id = ?"
      plan.where.sql shouldContain "i.archived_at IS NULL"
      plan.where.sql shouldContain "i.deleted_at IS NULL"
      plan.params shouldBe listOf(tenantId, projectId)
    }

    "compiles between predicates for custom numeric properties" {
      val plan =
        filter.build(
          WorkItemSearchScope(tenantId = tenantId),
          WorkItemQuery(
            where =
              ConditionNode.Predicate(
                field = QueryField.Property(apiId = null, code = "storyPoints"),
                op = QueryOperator.BETWEEN,
                value = QueryValue.Between(JsonPrimitive(1), JsonPrimitive(10)),
              )
          ),
        )

      plan.where.sql shouldContain "EXISTS"
      plan.where.sql shouldContain "pd.code = ?"
      plan.where.sql shouldContain "ipv.value_number >= ?"
      plan.where.sql shouldContain "ipv.value_number <= ?"
      plan.params shouldBe listOf(tenantId, "storyPoints", 1.0, 10.0)
    }

    "compiles relative date windows" {
      val plan =
        filter.build(
          WorkItemSearchScope(tenantId = tenantId),
          WorkItemQuery(
            where =
              ConditionNode.Predicate(
                field = QueryField.System("updatedAt"),
                op = QueryOperator.WITHIN,
                value =
                  QueryValue.RelativeDate(
                    amount = 7,
                    unit = RelativeDateUnit.DAY,
                    direction = DateDirection.PAST,
                    anchor = "date.now",
                  ),
              )
          ),
        )

      plan.where.sql shouldContain "i.updated_at >= (now() - (? * interval '1 day'))"
      plan.where.sql shouldContain "i.updated_at <= now()"
      plan.params shouldContain 7
    }

    "compiles jsonb array has_any predicates" {
      val plan =
        filter.build(
          WorkItemSearchScope(tenantId = tenantId),
          WorkItemQuery(
            where =
              ConditionNode.Predicate(
                field = QueryField.Property(apiId = null, code = "customerImpact"),
                op = QueryOperator.HAS_ANY,
                value =
                  QueryValue.Literal(
                    JsonArray(listOf(JsonPrimitive("enterprise"), JsonPrimitive("revenue")))
                  ),
              )
          ),
        )

      plan.where.sql shouldContain "ipv.value_array ?| ?"
      plan.params[1] shouldBe "customerImpact"
      (plan.params.last() as Array<*>).toList() shouldBe listOf("enterprise", "revenue")
    }

    "compiles explicit sort terms" {
      val plan =
        filter.build(
          WorkItemSearchScope(tenantId = tenantId),
          WorkItemQuery(
            sort = listOf(SortTerm(QueryField.System("updatedAt"), SortDirection.DESC))
          ),
        )

      plan.orderBySql shouldBe "ORDER BY i.updated_at DESC, i.api_id ASC"
    }
  })
