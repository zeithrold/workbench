package one.ztd.workbench.data.persistence.postgres.workitem.query

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PostgresWorkItemSqlTypesTest :
  StringSpec({
    "SqlFragment parenthesized wraps predicate sql" {
      SqlFragment(sql = "ipv.value_number > ?", params = listOf(1.0)).parenthesized().sql shouldBe
        "(ipv.value_number > ?)"
    }

    "PostgresWorkItemQueryPlan exposes composed sql parts" {
      val plan =
        PostgresWorkItemQueryPlan(
          fromSql = "FROM issues i",
          where = SqlFragment("i.tenant_id = ?", listOf("tenant")),
          orderBySql = "ORDER BY i.updated_at DESC",
          params = listOf("tenant"),
        )

      plan.fromSql shouldBe "FROM issues i"
      plan.orderBySql shouldBe "ORDER BY i.updated_at DESC"
      plan.params shouldBe listOf("tenant")
    }
  })
