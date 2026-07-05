package ink.doa.workbench.data.persistence.postgres.workitem.query

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
import ink.doa.workbench.core.workitem.query.WorkItemSearchGroupScope
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

    fun plan(scope: WorkItemSearchScope, query: WorkItemQuery) =
      filter.buildSearchPlan(scope, query, WorkItemSearchGroupScope(), cursor = null)

    "always applies trusted tenant project and lifecycle scope" {
      val built =
        plan(
          WorkItemSearchScope(tenantId = tenantId, projectId = projectId),
          WorkItemQuery(),
        )

      built.where.sql shouldContain "i.tenant_id = ?"
      built.where.sql shouldContain "i.project_id = ?"
      built.where.sql shouldContain "i.archived_at IS NULL"
      built.where.sql shouldContain "i.deleted_at IS NULL"
      built.params shouldBe listOf(tenantId, projectId)
    }

    "compiles between predicates for custom numeric properties" {
      val built =
        plan(
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

      built.where.sql shouldContain "EXISTS"
      built.where.sql shouldContain "pd.code = ?"
      built.where.sql shouldContain "ipv.value_number >= ?"
      built.where.sql shouldContain "ipv.value_number <= ?"
      built.params shouldBe listOf(tenantId, "storyPoints", 1.0, 10.0)
    }

    "compiles relative date windows" {
      val built =
        plan(
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

      built.where.sql shouldContain "i.updated_at >= (now() - (? * interval '1 day'))"
      built.where.sql shouldContain "i.updated_at <= now()"
      built.params shouldContain 7
    }

    "compiles jsonb array has_any predicates" {
      val built =
        plan(
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

      built.where.sql shouldContain "ipv.value_array ?| ?"
      built.params[1] shouldBe "customerImpact"
      (built.params.last() as Array<*>).toList() shouldBe listOf("enterprise", "revenue")
    }

    "compiles explicit sort terms" {
      val built =
        plan(
          WorkItemSearchScope(tenantId = tenantId),
          WorkItemQuery(
            sort = listOf(SortTerm(QueryField.System("updatedAt"), SortDirection.DESC))
          ),
        )

      built.orderBySql shouldBe "ORDER BY i.updated_at DESC, i.api_id ASC"
    }

    "compiles children issue type predicates as direct child existence checks" {
      val built =
        plan(
          WorkItemSearchScope(tenantId = tenantId),
          WorkItemQuery(
            where =
              ConditionNode.Predicate(
                field = QueryField.System("children.issueType"),
                op = QueryOperator.IN,
                value =
                  QueryValue.Literal(
                    JsonArray(listOf(JsonPrimitive("typ_bug"), JsonPrimitive("typ_task")))
                  ),
              )
          ),
        )

      built.where.sql shouldContain "FROM issue_hierarchy child_ih"
      built.where.sql shouldContain "JOIN issue_types child_type"
      built.where.sql shouldContain "child_type.api_id IN (?, ?)"
      built.params shouldBe listOf(tenantId, "typ_bug", "typ_task")
    }

    "compiles negative children issue type predicates as not exists" {
      val built =
        plan(
          WorkItemSearchScope(tenantId = tenantId),
          WorkItemQuery(
            where =
              ConditionNode.Predicate(
                field = QueryField.System("children.issueType"),
                op = QueryOperator.NOT_IN,
                value = QueryValue.Literal(JsonArray(listOf(JsonPrimitive("typ_bug")))),
              )
          ),
        )

      built.where.sql shouldContain "NOT (EXISTS"
      built.where.sql shouldContain "child_type.api_id IN (?)"
      built.params shouldBe listOf(tenantId, "typ_bug")
    }

    "compiles or not and property sort clauses" {
      val orPlan =
        plan(
          WorkItemSearchScope(tenantId = tenantId),
          WorkItemQuery(
            where =
              ConditionNode.Or(
                listOf(
                  ConditionNode.Predicate(
                    field = QueryField.System("title"),
                    op = QueryOperator.CONTAINS,
                    value = QueryValue.Literal(JsonPrimitive("bug")),
                  ),
                  ConditionNode.Not(
                    ConditionNode.Predicate(
                      field = QueryField.System("statusGroup"),
                      op = QueryOperator.EQ,
                      value = QueryValue.Literal(JsonPrimitive("done")),
                    )
                  ),
                )
              )
          ),
        )

      orPlan.where.sql shouldContain " OR "
      orPlan.where.sql shouldContain "NOT"

      val propertySort =
        plan(
          WorkItemSearchScope(tenantId = tenantId),
          WorkItemQuery(
            sort =
              listOf(
                SortTerm(QueryField.Property(apiId = null, code = "storyPoints"), SortDirection.ASC)
              )
          ),
        )

      propertySort.orderBySql shouldContain "ipv.value_number"
    }
  })
