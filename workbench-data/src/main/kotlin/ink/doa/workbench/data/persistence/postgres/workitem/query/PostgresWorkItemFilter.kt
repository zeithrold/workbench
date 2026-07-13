package ink.doa.workbench.data.persistence.postgres.workitem.query

import ink.doa.workbench.agile.workitem.WorkItemSearchScope
import ink.doa.workbench.agile.workitem.query.ConditionNode
import ink.doa.workbench.agile.workitem.query.QueryOperator
import ink.doa.workbench.agile.workitem.query.WorkItemGroupKeySupport
import ink.doa.workbench.agile.workitem.query.WorkItemQuery
import ink.doa.workbench.agile.workitem.query.WorkItemQueryFieldType
import ink.doa.workbench.agile.workitem.query.WorkItemSearchGroupScope
import ink.doa.workbench.kernel.common.pagination.WorkItemSearchCursor
import ink.doa.workbench.kernel.common.pagination.WorkItemSearchGroupCursor

class PostgresWorkItemFilter(private val fieldResolver: PostgresWorkItemFieldResolver) {
  private val operatorCompiler = PostgresWorkItemOperatorCompiler()

  fun buildSearchPlan(
    scope: WorkItemSearchScope,
    query: WorkItemQuery,
    groupScope: WorkItemSearchGroupScope,
    cursor: WorkItemSearchCursor?,
  ): PostgresWorkItemSearchPlan {
    val baseWhere = compileBaseWhere(scope, query)
    val scopeWhere = compileGroupScope(groupScope)
    val predicates = listOfNotNull(baseWhere, scopeWhere)
    val where = combine("AND", predicates)
    val sortStack = PostgresWorkItemSortStack.build(fieldResolver, query)
    val orderBySql = "ORDER BY ${PostgresWorkItemSortStack.orderByClause(sortStack)}"
    val sortParams = sortStack.flatMap { it.params }
    val cursorWhere = cursor?.let {
      val cursorValues = decodeCursorValues(it, sortStack.size)
      PostgresWorkItemSearchAfter.afterCursor(sortStack, cursorValues)
    }
    val allPredicates = listOfNotNull(where, cursorWhere?.let { combine("AND", listOf(it)) })
    val finalWhere = combine("AND", allPredicates)
    return PostgresWorkItemSearchPlan(
      fromSql = FROM_SQL,
      where = finalWhere,
      orderBySql = orderBySql,
      sortStack = sortStack,
      params = finalWhere.params,
      sortParams = sortParams,
    )
  }

  fun buildGroupsPlan(
    scope: WorkItemSearchScope,
    query: WorkItemQuery,
    groupCursor: WorkItemSearchGroupCursor?,
    fetchLimit: Int,
  ): PostgresWorkItemGroupsPlan {
    val group =
      query.group ?: throw IllegalArgumentException("Work item groups query requires query.group.")
    val baseWhere = compileBaseWhere(scope, query)
    val groupField = fieldResolver.resolvePostgresField(group.field)
    val groupSql = groupField.groupSql
    val direction = if (group.direction.name == "ASC") "ASC" else "DESC"
    val nullsClause = if (group.direction.name == "ASC") "NULLS LAST" else "NULLS FIRST"
    val groupParams =
      when (groupField) {
        is PostgresWorkItemField.Property -> groupField.identityParams
        is PostgresWorkItemField.System -> emptyList()
      }
    val cursorPredicate =
      when {
        groupCursor == null -> null
        groupCursor.value is kotlinx.serialization.json.JsonNull ->
          SqlFragment("grouped.group_value IS NOT NULL", emptyList())
        group.direction.name == "ASC" ->
          SqlFragment(
            "grouped.group_value > ?",
            listOf(WorkItemGroupKeySupport.toJdbcValue(groupCursor.value)),
          )
        else ->
          SqlFragment(
            "grouped.group_value < ?",
            listOf(WorkItemGroupKeySupport.toJdbcValue(groupCursor.value)),
          )
      }
    return PostgresWorkItemGroupsPlan(
      fromSql = FROM_SQL,
      where = baseWhere,
      groupSql = groupSql,
      groupParams = groupParams,
      orderDirection = direction,
      nullsClause = nullsClause,
      fetchLimit = fetchLimit,
      params = baseWhere.params,
      cursorPredicate = cursorPredicate,
    )
  }

  private fun decodeCursorValues(cursor: WorkItemSearchCursor, expectedSize: Int): List<Any?> {
    val values = cursor.sortValues.map(WorkItemGroupKeySupport::toJdbcValue).toMutableList()
    if (values.size + 1 == expectedSize) {
      values += cursor.apiId
    }
    require(values.size == expectedSize) {
      "Cursor sort value count ${values.size} does not match expected $expectedSize."
    }
    return values
  }

  private fun compileBaseWhere(scope: WorkItemSearchScope, query: WorkItemQuery): SqlFragment {
    val basePredicates =
      mutableListOf<SqlFragment>(SqlFragment("i.tenant_id = ?", listOf(scope.tenantId)))
    scope.projectId?.let { basePredicates += SqlFragment("i.project_id = ?", listOf(it)) }
    if (!scope.includeArchived) basePredicates += SqlFragment("i.archived_at IS NULL")
    if (!scope.includeDeleted) basePredicates += SqlFragment("i.deleted_at IS NULL")
    query.where?.let { basePredicates += compileCondition(it) }
    return combine("AND", basePredicates)
  }

  private fun compileGroupScope(groupScope: WorkItemSearchGroupScope): SqlFragment? {
    if (groupScope.includeGroupKeys.isEmpty() && groupScope.excludeGroupKeys.isEmpty()) {
      return null
    }
    val keys =
      if (groupScope.includeGroupKeys.isNotEmpty()) groupScope.includeGroupKeys
      else groupScope.excludeGroupKeys
    val compiled = keys.map(::compileConditionNodePredicate)
    val combined = combine("OR", compiled)
    return if (groupScope.includeGroupKeys.isNotEmpty()) {
      combined
    } else {
      SqlFragment("NOT (${combined.sql})", combined.params)
    }
  }

  private fun compileConditionNodePredicate(predicate: ConditionNode.Predicate): SqlFragment =
    compilePredicate(predicate)

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
      throw ink.doa.workbench.kernel.common.errors.InvalidRequestException(
        ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
          .WORK_ITEM_QUERY_LONG_TEXT_REQUIRES_ELASTICSEARCH
      )
    }
    val condition =
      if (field is PostgresWorkItemField.System && field.predicateCompiler != null) {
        field.predicateCompiler.invoke(predicate.op, predicate.value)
      } else {
        operatorCompiler.compile(
          field.valueSql,
          field.definition.type,
          predicate.op,
          predicate.value,
        )
      }
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

  private fun combine(operator: String, fragments: List<SqlFragment>): SqlFragment {
    val params = fragments.flatMap { it.params }
    return SqlFragment(fragments.joinToString(" $operator ") { "(${it.sql})" }, params)
  }

  companion object {
    private val TEXT_SEARCH_OPERATORS =
      setOf(QueryOperator.CONTAINS, QueryOperator.NOT_CONTAINS, QueryOperator.MATCHES)

    internal const val FROM_SQL =
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
