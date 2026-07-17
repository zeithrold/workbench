package one.ztd.workbench.data.persistence.postgres.workitem.query

import one.ztd.workbench.agile.workitem.query.NullOrdering
import one.ztd.workbench.agile.workitem.query.QueryField
import one.ztd.workbench.agile.workitem.query.SortDirection
import one.ztd.workbench.agile.workitem.query.WorkItemQuery

data class PostgresSortSpec(
  val field: QueryField,
  val sql: String,
  val direction: SortDirection,
  val nulls: NullOrdering?,
  val params: List<Any?> = emptyList(),
)

object PostgresWorkItemSortStack {
  fun build(resolver: PostgresWorkItemFieldResolver, query: WorkItemQuery): List<PostgresSortSpec> {
    val specs = mutableListOf<PostgresSortSpec>()
    query.group?.let { specs += toSpec(resolver, it.field, it.direction, null) }
    if (query.sort.isEmpty() && query.group == null) {
      specs += toSpec(resolver, QueryField.System("updatedAt"), SortDirection.DESC, null)
    } else {
      query.sort.forEach { term ->
        specs += toSpec(resolver, term.field, term.direction, term.nulls)
      }
    }
    specs += toSpec(resolver, QueryField.System("apiId"), SortDirection.ASC, null)
    return specs
  }

  private fun toSpec(
    resolver: PostgresWorkItemFieldResolver,
    field: QueryField,
    direction: SortDirection,
    nulls: NullOrdering?,
  ): PostgresSortSpec {
    val postgresField = resolver.resolvePostgresField(field)
    val params =
      when (postgresField) {
        is PostgresWorkItemField.Property -> postgresField.identityParams
        is PostgresWorkItemField.System -> emptyList()
      }
    return PostgresSortSpec(
      field = field,
      sql = postgresField.sortSql,
      direction = direction,
      nulls = nulls,
      params = params,
    )
  }

  fun orderByClause(specs: List<PostgresSortSpec>): String =
    specs.joinToString(", ") { spec ->
      val direction = if (spec.direction == SortDirection.ASC) "ASC" else "DESC"
      val nulls = spec.nulls?.wireName?.uppercase()?.let { " NULLS $it" }.orEmpty()
      "${spec.sql} $direction$nulls"
    }
}
