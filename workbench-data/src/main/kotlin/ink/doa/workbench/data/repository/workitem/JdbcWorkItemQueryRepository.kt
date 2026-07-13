package ink.doa.workbench.data.repository.workitem

import ink.doa.workbench.agile.workitem.WorkItemQueryRepository
import ink.doa.workbench.agile.workitem.WorkItemSearchGroupsPageRequest
import ink.doa.workbench.agile.workitem.WorkItemSearchPageRequest
import ink.doa.workbench.agile.workitem.WorkItemSearchScope
import ink.doa.workbench.agile.workitem.model.WorkItemSearchGroupBucket
import ink.doa.workbench.agile.workitem.model.WorkItemSearchGroupsPage
import ink.doa.workbench.agile.workitem.model.WorkItemSearchHit
import ink.doa.workbench.agile.workitem.model.WorkItemSearchResult
import ink.doa.workbench.agile.workitem.query.QueryField
import ink.doa.workbench.agile.workitem.query.WorkItemGroupKeySupport
import ink.doa.workbench.agile.workitem.query.WorkItemQuery
import ink.doa.workbench.agile.workitem.query.WorkItemQueryValidator
import ink.doa.workbench.agile.workitem.query.WorkItemSearchGroupScope
import ink.doa.workbench.data.persistence.postgres.toPreparedStatementSetter
import ink.doa.workbench.data.persistence.postgres.workitem.query.JdbcPostgresWorkItemFieldResolver
import ink.doa.workbench.data.persistence.postgres.workitem.query.PostgresWorkItemFilter
import ink.doa.workbench.data.persistence.postgres.workitem.query.PostgresWorkItemGroupsPlan
import ink.doa.workbench.data.persistence.postgres.workitem.query.PostgresWorkItemSearchPlan
import ink.doa.workbench.data.persistence.postgres.workitem.query.WorkItemSearchHitRowMapper
import ink.doa.workbench.kernel.common.pagination.WorkItemSearchCursor
import ink.doa.workbench.kernel.common.pagination.WorkItemSearchGroupCursor
import java.sql.ResultSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

@Repository
class JdbcWorkItemQueryRepository(
  private val jdbcTemplate: JdbcTemplate,
  private val ioDispatcher: CoroutineDispatcher,
  private val groupLabelResolver: WorkItemGroupLabelResolver,
) : WorkItemQueryRepository {
  override suspend fun search(
    scope: WorkItemSearchScope,
    query: WorkItemQuery,
    groupScope: WorkItemSearchGroupScope,
    page: WorkItemSearchPageRequest,
  ): WorkItemSearchResult =
    withContext(ioDispatcher) {
      val resolver = JdbcPostgresWorkItemFieldResolver(jdbcTemplate, scope.tenantId)
      WorkItemQueryValidator(resolver).validate(query, groupScope)
      val plan =
        PostgresWorkItemFilter(resolver).buildSearchPlan(scope, query, groupScope, page.cursor)
      val fetchLimit = page.limit + 1
      val rows = queryRows(plan.params + plan.sortParams + listOf(fetchLimit), plan)
      val pageRows = rows.take(page.limit)
      val hits = enrichHits(scope, query.group?.field, pageRows)
      WorkItemSearchResult(hits = hits, nextCursor = nextSearchCursor(plan, page, rows, pageRows))
    }

  override suspend fun searchGroups(
    scope: WorkItemSearchScope,
    query: WorkItemQuery,
    page: WorkItemSearchGroupsPageRequest,
  ): WorkItemSearchGroupsPage =
    withContext(ioDispatcher) {
      val resolver = JdbcPostgresWorkItemFieldResolver(jdbcTemplate, scope.tenantId)
      WorkItemQueryValidator(resolver).validate(query)
      val groupField =
        requireNotNull(query.group) { "Work item groups query requires query.group." }.field
      val fetchLimit = page.groupLimit + 1
      val plan =
        PostgresWorkItemFilter(resolver).buildGroupsPlan(scope, query, page.groupCursor, fetchLimit)
      val params =
        plan.groupParams +
          plan.params +
          (plan.cursorPredicate?.params.orEmpty()) +
          listOf(fetchLimit)
      val rows = queryRows(groupsSql(plan), params, GroupBucketRowMapper)
      val pageRows = rows.take(page.groupLimit)
      val groups = pageRows.map { row ->
        val groupKey = WorkItemGroupKeySupport.keyFromBucketValue(groupField, row.groupValue)
        WorkItemSearchGroupBucket(
          key = groupKey,
          label = groupLabelResolver.resolve(scope.tenantId, groupKey),
          count = row.count,
        )
      }
      val nextGroupCursor =
        if (rows.size > page.groupLimit) {
          val last = pageRows.last()
          WorkItemSearchGroupCursor(value = WorkItemGroupKeySupport.toJsonElement(last.groupValue))
        } else {
          null
        }
      WorkItemSearchGroupsPage(
        groups = groups,
        nextGroupCursor = nextGroupCursor,
        truncated = rows.size > page.groupLimit,
      )
    }

  private fun queryRows(
    params: List<Any?>,
    plan: PostgresWorkItemSearchPlan,
  ): List<WorkItemSearchHit> {
    val sql =
      """
      $SEARCH_SELECT_SQL
      ${plan.fromSql}
      WHERE ${plan.where.sql}
      ${plan.orderBySql}
      LIMIT ?
      """
        .trimIndent()
    return queryRows(sql, params, WorkItemSearchHitRowMapper)
  }

  private fun enrichHits(
    scope: WorkItemSearchScope,
    groupField: QueryField?,
    pageRows: List<WorkItemSearchHit>,
  ): List<WorkItemSearchHit> {
    if (groupField == null) return pageRows
    return pageRows.map { hit ->
      val bucketValue = WorkItemSearchHitSortValues.bucketValueForField(groupField, hit)
      val groupKey = WorkItemGroupKeySupport.keyFromBucketValue(groupField, bucketValue)
      hit.copy(
        groupKey = groupKey,
        groupLabel = groupLabelResolver.resolve(scope.tenantId, groupKey),
      )
    }
  }

  private fun nextSearchCursor(
    plan: PostgresWorkItemSearchPlan,
    page: WorkItemSearchPageRequest,
    rows: List<WorkItemSearchHit>,
    pageRows: List<WorkItemSearchHit>,
  ): WorkItemSearchCursor? {
    if (rows.size <= page.limit) return null
    val last = pageRows.last()
    return WorkItemSearchCursor(
      sortValues =
        plan.sortStack.map { spec -> WorkItemSearchHitSortValues.valueForField(spec.field, last) },
      apiId = last.apiId,
    )
  }

  private fun groupsSql(plan: PostgresWorkItemGroupsPlan): String {
    val cursorSql = plan.cursorPredicate?.let { "WHERE ${it.sql}" }.orEmpty()
    return """
      SELECT grouped.group_value, grouped.cnt
      FROM (
        SELECT ${plan.groupSql} AS group_value, COUNT(*) AS cnt
        ${plan.fromSql}
        WHERE ${plan.where.sql}
        GROUP BY 1
      ) grouped
      $cursorSql
      ORDER BY grouped.group_value ${plan.orderDirection} ${plan.nullsClause}
      LIMIT ?
      """
      .trimIndent()
  }

  private fun <T> queryRows(
    sql: String,
    params: List<Any?>,
    rowMapper: RowMapper<T>,
  ): List<T> = jdbcTemplate.query(sql, params.toPreparedStatementSetter(), rowMapper)

  private data class GroupBucketRow(val groupValue: Any?, val count: Long)

  private object GroupBucketRowMapper : RowMapper<GroupBucketRow> {
    override fun mapRow(rs: ResultSet, rowNum: Int): GroupBucketRow =
      GroupBucketRow(
        groupValue = rs.getObject("group_value"),
        count = rs.getLong("cnt"),
      )
  }

  private companion object {
    private const val SEARCH_SELECT_SQL =
      """
      SELECT
        i.api_id,
        COALESCE(keya.issue_key, p.identifier || '-' || i.sequence_no) AS issue_key,
        i.title,
        i.description_document,
        p.api_id AS project_api_id,
        itype.api_id AS issue_type_api_id,
        itc.api_id AS issue_type_config_api_id,
        st.api_id AS status_api_id,
        st.status_group,
        pri.api_id AS priority_api_id,
        reporter.api_id AS reporter_api_id,
        assignee.api_id AS assignee_api_id,
        sprint.api_id AS sprint_api_id,
        i.created_at,
        i.updated_at,
        i.properties_snapshot::text AS properties_snapshot
      """
  }
}
