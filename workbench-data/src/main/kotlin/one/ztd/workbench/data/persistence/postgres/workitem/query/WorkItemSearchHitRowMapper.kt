package one.ztd.workbench.data.persistence.postgres.workitem.query

import java.sql.ResultSet
import java.sql.Timestamp
import java.time.OffsetDateTime
import kotlinx.serialization.json.Json
import one.ztd.workbench.agile.workitem.model.WorkItemIssueTypeSummary
import one.ztd.workbench.agile.workitem.model.WorkItemPrioritySummary
import one.ztd.workbench.agile.workitem.model.WorkItemSearchHit
import one.ztd.workbench.agile.workitem.model.WorkItemSprintSummary
import one.ztd.workbench.agile.workitem.model.WorkItemStatusSummary
import one.ztd.workbench.agile.workitem.model.WorkItemUserSummary
import one.ztd.workbench.agile.workitem.richtext.RichTextDocument
import org.springframework.jdbc.core.RowMapper

object WorkItemSearchHitRowMapper : RowMapper<WorkItemSearchHit> {
  private val json = Json { ignoreUnknownKeys = true }

  override fun mapRow(rs: ResultSet, rowNum: Int): WorkItemSearchHit =
    WorkItemSearchHit(
      databaseId = rs.getObject("issue_id", java.util.UUID::class.java),
      apiId = rs.getString("api_id"),
      key = rs.getString("issue_key"),
      title = rs.getString("title"),
      description = parseDescription(rs.getString("description_document")),
      projectApiId = rs.getString("project_api_id"),
      issueType = rs.issueTypeSummary(),
      issueTypeConfigApiId = rs.getString("issue_type_config_api_id"),
      status = rs.statusSummary(),
      priority =
        rs.getString("priority_api_id")?.let { id ->
          WorkItemPrioritySummary(
            id = id,
            code = rs.getString("priority_code"),
            name = rs.getString("priority_name"),
            icon = rs.getString("priority_icon"),
            color = rs.getString("priority_color"),
          )
        },
      reporter =
        WorkItemUserSummary(
          id = rs.getString("reporter_api_id"),
          displayName = rs.getString("reporter_display_name"),
        ),
      assignee =
        rs.getString("assignee_api_id")?.let { id ->
          WorkItemUserSummary(id = id, displayName = rs.getString("assignee_display_name"))
        },
      sprint =
        rs.getString("sprint_api_id")?.let { id ->
          WorkItemSprintSummary(
            id = id,
            name = rs.getString("sprint_name"),
            status = rs.getString("sprint_status"),
            startAt = rs.offsetDateTimeOrNull("sprint_start_at"),
            endAt = rs.offsetDateTimeOrNull("sprint_end_at"),
          )
        },
      createdAt =
        rs.getObject("created_at", OffsetDateTime::class.java)
          ?: rs.getTimestamp("created_at").toOffsetDateTime(),
      updatedAt =
        rs.getObject("updated_at", OffsetDateTime::class.java)
          ?: rs.getTimestamp("updated_at").toOffsetDateTime(),
      properties = emptyMap(),
    )

  private fun parseDescription(value: String?): RichTextDocument? = value?.let {
    json.decodeFromString(RichTextDocument.serializer(), it)
  }

  private fun ResultSet.issueTypeSummary() =
    WorkItemIssueTypeSummary(
      id = getString("issue_type_api_id"),
      code = getString("issue_type_code"),
      name = getString("issue_type_name"),
      icon = getString("issue_type_icon"),
      color = getString("issue_type_color"),
    )

  private fun ResultSet.statusSummary() =
    WorkItemStatusSummary(
      id = getString("status_api_id"),
      code = getString("status_code"),
      name = getString("status_name"),
      group = getString("status_group"),
      color = getString("status_color"),
      terminal = getBoolean("status_terminal"),
    )

  private fun Timestamp.toOffsetDateTime(): OffsetDateTime =
    toInstant().atOffset(java.time.ZoneOffset.UTC)

  private fun ResultSet.offsetDateTimeOrNull(column: String): OffsetDateTime? =
    getObject(column, OffsetDateTime::class.java) ?: getTimestamp(column)?.toOffsetDateTime()
}
