package ink.doa.workbench.data.persistence.postgres.workitem.query

import ink.doa.workbench.core.workitem.model.WorkItemSearchHit
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.OffsetDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.springframework.jdbc.core.RowMapper

object WorkItemSearchHitRowMapper : RowMapper<WorkItemSearchHit> {
  private val json = Json { ignoreUnknownKeys = true }

  override fun mapRow(rs: ResultSet, rowNum: Int): WorkItemSearchHit =
    WorkItemSearchHit(
      apiId = rs.getString("api_id"),
      key = rs.getString("issue_key"),
      title = rs.getString("title"),
      description = rs.getString("description"),
      projectApiId = rs.getString("project_api_id"),
      issueTypeApiId = rs.getString("issue_type_api_id"),
      issueTypeConfigApiId = rs.getString("issue_type_config_api_id"),
      statusApiId = rs.getString("status_api_id"),
      statusGroup = rs.getString("status_group"),
      priorityApiId = rs.getString("priority_api_id"),
      reporterApiId = rs.getString("reporter_api_id"),
      assigneeApiId = rs.getString("assignee_api_id"),
      sprintApiId = rs.getString("sprint_api_id"),
      createdAt =
        rs.getObject("created_at", OffsetDateTime::class.java)
          ?: rs.getTimestamp("created_at").toOffsetDateTime(),
      updatedAt =
        rs.getObject("updated_at", OffsetDateTime::class.java)
          ?: rs.getTimestamp("updated_at").toOffsetDateTime(),
      properties = parseProperties(rs.getString("properties_snapshot")),
    )

  private fun parseProperties(value: String?): JsonObject =
    value?.let { json.parseToJsonElement(it).jsonObject } ?: JsonObject(emptyMap())

  private fun Timestamp.toOffsetDateTime(): OffsetDateTime =
    toInstant().atOffset(java.time.ZoneOffset.UTC)
}
