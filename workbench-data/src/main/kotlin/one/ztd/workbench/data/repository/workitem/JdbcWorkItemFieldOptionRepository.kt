package one.ztd.workbench.data.repository.workitem

import java.sql.ResultSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import one.ztd.workbench.agile.workitem.WorkItemFieldOption
import one.ztd.workbench.agile.workitem.WorkItemFieldOptionKind
import one.ztd.workbench.agile.workitem.WorkItemFieldOptionQuery
import one.ztd.workbench.agile.workitem.WorkItemFieldOptionRepository
import one.ztd.workbench.data.persistence.postgres.toPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class JdbcWorkItemFieldOptionRepository(
  private val jdbc: JdbcTemplate,
  private val ioDispatcher: CoroutineDispatcher,
) : WorkItemFieldOptionRepository {
  override suspend fun list(query: WorkItemFieldOptionQuery): List<WorkItemFieldOption> =
    withContext(ioDispatcher) {
      val statement = optionStatement(query)
      jdbc.query(
        statement.sql,
        statement.parameters.toPreparedStatementSetter(),
        { rs, _ -> rs.toOption() },
      )
    }

  private fun optionStatement(query: WorkItemFieldOptionQuery): OptionStatement =
    when (query.kind) {
      WorkItemFieldOptionKind.USER -> userStatement(query)
      WorkItemFieldOptionKind.PRIORITY -> priorityStatement(query)
      WorkItemFieldOptionKind.SPRINT -> sprintStatement(query)
      WorkItemFieldOptionKind.FIXED -> fixedStatement(query)
      WorkItemFieldOptionKind.PROJECT -> projectStatement(query)
      WorkItemFieldOptionKind.WORK_ITEM -> workItemStatement(query)
    }

  private fun userStatement(query: WorkItemFieldOptionQuery): OptionStatement {
    val search = "%${query.search.orEmpty().lowercase()}%"
    return OptionStatement(
      """
          SELECT u.api_id AS id, u.display_name AS label, u.primary_email AS description,
                 NULL AS color, NULL AS icon, NULL AS status
          FROM tenant_members tm JOIN users u ON u.id = tm.user_id
          WHERE tm.tenant_id = ? AND tm.status = 'active'
            AND tm.archived_at IS NULL AND tm.deleted_at IS NULL
            AND u.archived_at IS NULL AND u.deleted_at IS NULL
            AND (? = '%%' OR lower(u.display_name) LIKE ? OR lower(coalesce(u.primary_email, '')) LIKE ?)
          ORDER BY lower(u.display_name), u.api_id
          $PAGING
          """
        .trimIndent(),
      listOf(query.tenantId, search, search, search, query.limit, query.offset),
    )
  }

  private fun priorityStatement(query: WorkItemFieldOptionQuery): OptionStatement {
    val search = "%${query.search.orEmpty().lowercase()}%"
    return OptionStatement(
      """
          SELECT api_id AS id, name AS label, code AS description, color, icon, NULL AS status
          FROM priorities
          WHERE tenant_id = ? AND is_active = true
            AND (? = '%%' OR lower(name) LIKE ? OR lower(code) LIKE ?)
          ORDER BY rank, api_id
          $PAGING
          """
        .trimIndent(),
      listOf(query.tenantId, search, search, search, query.limit, query.offset),
    )
  }

  private fun sprintStatement(query: WorkItemFieldOptionQuery): OptionStatement {
    val search = "%${query.search.orEmpty().lowercase()}%"
    return OptionStatement(
      """
          SELECT api_id AS id, name AS label, NULL AS description, NULL AS color, NULL AS icon, status
          FROM sprints
          WHERE tenant_id = ? AND project_id = ? AND archived_at IS NULL AND deleted_at IS NULL
            AND (? = '%%' OR lower(name) LIKE ?)
          ORDER BY CASE status WHEN 'active' THEN 0 WHEN 'planned' THEN 1 ELSE 2 END, start_at DESC NULLS LAST, api_id
          $PAGING
          """
        .trimIndent(),
      listOf(query.tenantId, query.projectId, search, search, query.limit, query.offset),
    )
  }

  private fun fixedStatement(query: WorkItemFieldOptionQuery): OptionStatement {
    val search = "%${query.search.orEmpty().lowercase()}%"
    return OptionStatement(
      """
          SELECT api_id AS id, label, code AS description, color, NULL AS icon, NULL AS status
          FROM property_options
          WHERE tenant_id = ? AND property_id = ? AND is_active = true
            AND (? = '%%' OR lower(label) LIKE ? OR lower(code) LIKE ?)
          ORDER BY rank, api_id
          $PAGING
          """
        .trimIndent(),
      listOf(
        query.tenantId,
        requireNotNull(query.propertyId),
        search,
        search,
        search,
        query.limit,
        query.offset,
      ),
    )
  }

  private fun projectStatement(query: WorkItemFieldOptionQuery): OptionStatement {
    val search = "%${query.search.orEmpty().lowercase()}%"
    return OptionStatement(
      """
          SELECT api_id AS id, name AS label, identifier AS description, NULL AS color, NULL AS icon, status
          FROM projects
          WHERE tenant_id = ? AND archived_at IS NULL AND deleted_at IS NULL
            AND (? = '%%' OR lower(name) LIKE ? OR lower(identifier) LIKE ?)
          ORDER BY lower(name), api_id
          $PAGING
          """
        .trimIndent(),
      listOf(query.tenantId, search, search, search, query.limit, query.offset),
    )
  }

  private fun workItemStatement(query: WorkItemFieldOptionQuery): OptionStatement {
    val search = "%${query.search.orEmpty().lowercase()}%"
    return OptionStatement(
      """
          SELECT i.api_id AS id, i.title AS label,
                 concat(p.identifier, '-', i.sequence_no) AS description,
                 NULL AS color, NULL AS icon, NULL AS status
          FROM issues i JOIN projects p ON p.id = i.project_id
          WHERE i.tenant_id = ? AND i.project_id = ? AND i.archived_at IS NULL AND i.deleted_at IS NULL
            AND (? = '%%' OR lower(i.title) LIKE ? OR lower(concat(p.identifier, '-', i.sequence_no)) LIKE ?)
          ORDER BY i.updated_at DESC, i.api_id
          $PAGING
          """
        .trimIndent(),
      listOf(
        query.tenantId,
        query.projectId,
        search,
        search,
        search,
        query.limit,
        query.offset,
      ),
    )
  }

  private fun ResultSet.toOption() =
    WorkItemFieldOption(
      id = getString("id"),
      label = getString("label"),
      description = getString("description"),
      color = getString("color"),
      icon = getString("icon"),
      status = getString("status"),
    )

  private data class OptionStatement(val sql: String, val parameters: List<Any?>)

  private companion object {
    const val PAGING = " LIMIT ? OFFSET ?"
  }
}
