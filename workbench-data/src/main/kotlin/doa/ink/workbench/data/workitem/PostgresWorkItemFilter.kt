package doa.ink.workbench.data.workitem

import doa.ink.workbench.core.common.errors.InvalidRequestException
import doa.ink.workbench.core.workitem.WorkItemSearchScope
import doa.ink.workbench.core.workitem.query.ConditionNode
import doa.ink.workbench.core.workitem.query.QueryOperator
import doa.ink.workbench.core.workitem.query.SortDirection
import doa.ink.workbench.core.workitem.query.WorkItemQuery
import doa.ink.workbench.core.workitem.query.WorkItemQueryFieldType

class PostgresWorkItemFilter(private val fieldResolver: PostgresWorkItemFieldResolver) {
  private val operatorCompiler = PostgresWorkItemOperatorCompiler()

  fun build(scope: WorkItemSearchScope, query: WorkItemQuery): PostgresWorkItemQueryPlan {
    val basePredicates =
      mutableListOf<SqlFragment>(SqlFragment("i.tenant_id = ?", listOf(scope.tenantId)))
    scope.projectId?.let { basePredicates += SqlFragment("i.project_id = ?", listOf(it)) }
    if (!scope.includeArchived) basePredicates += SqlFragment("i.archived_at IS NULL")
    if (!scope.includeDeleted) basePredicates += SqlFragment("i.deleted_at IS NULL")
    query.where?.let { basePredicates += compileCondition(it) }
    val where = combine("AND", basePredicates)
    return PostgresWorkItemQueryPlan(
      fromSql = FROM_SQL,
      where = where,
      orderBySql = compileOrderBy(query),
      params = where.params,
    )
  }

  private fun compileCondition(node: ConditionNode): SqlFragment =
    when (node) {
      is ConditionNode.And -> combine("AND", node.args.map(::compileCondition))
      is ConditionNode.Or -> combine("OR", node.args.map(::compileCondition))
      is ConditionNode.Not -> {
        val child = compileCondition(node.arg)
        SqlFragment("NOT (${child.sql})", child.params)
      }
      is ConditionNode.Predicate -> compilePredicate(node)
    }

  private fun compilePredicate(predicate: ConditionNode.Predicate): SqlFragment {
    val field = fieldResolver.resolvePostgresField(predicate.field)
    if (
      field.definition.type == WorkItemQueryFieldType.LONG_TEXT &&
        predicate.op in TEXT_SEARCH_OPERATORS
    ) {
      throw InvalidRequestException("Long text work item predicates require Elasticsearch.")
    }
    val condition =
      operatorCompiler.compile(field.valueSql, field.definition.type, predicate.op, predicate.value)
    return when (field) {
      is PostgresWorkItemField.System -> condition
      is PostgresWorkItemField.Property -> {
        val params = mutableListOf<Any?>()
        params.addAll(field.identityParams)
        params.addAll(condition.params)
        SqlFragment(
          """
          EXISTS (
            SELECT 1
            FROM issue_property_values ipv
            JOIN property_definitions pd ON pd.id = ipv.property_id
            LEFT JOIN property_options option_value ON option_value.id = ipv.value_option_id
            LEFT JOIN users user_value ON user_value.id = ipv.value_user_id
            LEFT JOIN projects project_value ON project_value.id = ipv.value_project_id
            LEFT JOIN issues issue_value ON issue_value.id = ipv.value_issue_id
            WHERE ipv.issue_id = i.id
              AND ipv.tenant_id = i.tenant_id
              AND ${field.identitySql}
              AND ${condition.sql}
          )
          """
            .trimIndent(),
          params,
        )
      }
    }
  }

  private fun compileOrderBy(query: WorkItemQuery): String {
    if (query.sort.isEmpty()) return "ORDER BY i.updated_at DESC, i.api_id ASC"
    val terms =
      query.sort.map { term ->
        val field = fieldResolver.resolvePostgresField(term.field)
        if (!field.definition.sortable) {
          throw InvalidRequestException("Field ${term.field.canonicalName} is not sortable.")
        }
        val direction = if (term.direction == SortDirection.ASC) "ASC" else "DESC"
        val nulls = term.nulls?.wireName?.uppercase()?.let { " NULLS $it" } ?: ""
        "${field.sortSql} $direction$nulls"
      }
    return "ORDER BY ${terms.joinToString(", ")}, i.api_id ASC"
  }

  private fun combine(operator: String, fragments: List<SqlFragment>): SqlFragment {
    val params = fragments.flatMap { it.params }
    return SqlFragment(fragments.joinToString(" $operator ") { "(${it.sql})" }, params)
  }

  companion object {
    private val TEXT_SEARCH_OPERATORS =
      setOf(QueryOperator.CONTAINS, QueryOperator.NOT_CONTAINS, QueryOperator.MATCHES)

    private const val FROM_SQL =
      """
      FROM issues i
      JOIN projects p ON p.id = i.project_id
      JOIN issue_types itype ON itype.id = i.issue_type_id
      JOIN issue_type_configs itc ON itc.id = i.issue_type_config_id
      JOIN issue_statuses st ON st.id = i.status_id
      LEFT JOIN priorities pri ON pri.id = i.priority_id
      JOIN users reporter ON reporter.id = i.reporter_id
      LEFT JOIN users assignee ON assignee.id = i.assignee_id
      LEFT JOIN users created_by_user ON created_by_user.id = i.created_by
      LEFT JOIN users updated_by_user ON updated_by_user.id = i.updated_by
      LEFT JOIN sprints sprint ON sprint.id = i.sprint_id
      LEFT JOIN issue_key_aliases keya ON keya.issue_id = i.id AND keya.is_current = true
      """
  }
}
