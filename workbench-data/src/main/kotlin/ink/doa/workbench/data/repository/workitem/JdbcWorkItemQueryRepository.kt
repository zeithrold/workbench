package ink.doa.workbench.data.workitem

import ink.doa.workbench.core.workitem.WorkItemQueryRepository
import ink.doa.workbench.core.workitem.WorkItemSearchPageRequest
import ink.doa.workbench.core.workitem.WorkItemSearchScope
import ink.doa.workbench.core.workitem.model.WorkItemSearchPage
import ink.doa.workbench.core.workitem.model.WorkItemSearchPageInfo
import ink.doa.workbench.core.workitem.model.WorkItemSearchResult
import ink.doa.workbench.core.workitem.query.WorkItemQuery
import ink.doa.workbench.core.workitem.query.WorkItemQueryValidator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class JdbcWorkItemQueryRepository(
  private val jdbcTemplate: JdbcTemplate,
  private val ioDispatcher: CoroutineDispatcher,
) : WorkItemQueryRepository {
  override suspend fun search(
    scope: WorkItemSearchScope,
    query: WorkItemQuery,
    page: WorkItemSearchPageRequest,
  ): WorkItemSearchPage =
    withContext(ioDispatcher) {
      val resolver = JdbcPostgresWorkItemFieldResolver(jdbcTemplate, scope.tenantId)
      WorkItemQueryValidator(resolver).validate(query)
      val plan = PostgresWorkItemFilter(resolver).build(scope, query)
      val selectSql =
        """
        SELECT
          i.api_id,
          COALESCE(keya.issue_key, p.identifier || '-' || i.sequence_no) AS issue_key,
          i.title,
          i.description,
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
        ${plan.fromSql}
        WHERE ${plan.where.sql}
        ${plan.orderBySql}
        LIMIT ? OFFSET ?
        """
          .trimIndent()
      val params = plan.params + listOf(page.limit, page.offset)
      val hits = queryRows(selectSql, params, WorkItemSearchHitRowMapper)
      val countSql = "SELECT COUNT(*) ${plan.fromSql} WHERE ${plan.where.sql}"
      val total = queryForLong(countSql, plan.params)
      WorkItemSearchPage(
        result = WorkItemSearchResult(hits = hits, total = total),
        page =
          WorkItemSearchPageInfo(
            limit = page.limit,
            offset = page.offset,
            nextOffset = if (hits.size == page.limit) page.offset + page.limit else null,
          ),
      )
    }

  // Spread is isolated here so repository search stays free of detekt SpreadOperator noise.
  @Suppress("SpreadOperator")
  private fun <T> queryRows(
    sql: String,
    params: List<Any?>,
    rowMapper: org.springframework.jdbc.core.RowMapper<T>,
  ): List<T> = jdbcTemplate.query(sql, rowMapper, *params.toTypedArray())

  @Suppress("SpreadOperator")
  private fun queryForLong(sql: String, params: List<Any?>): Long =
    checkNotNull(jdbcTemplate.queryForObject(sql, Long::class.java, *params.toTypedArray()))
}
