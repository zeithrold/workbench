package one.ztd.workbench.data.repository.workitem

import java.sql.ResultSet
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import one.ztd.workbench.agile.workitem.model.WorkItemPropertyPresentation
import one.ztd.workbench.agile.workitem.model.WorkItemPropertySummary
import one.ztd.workbench.data.persistence.postgres.toPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class WorkItemPropertyPresentationLoader(private val jdbcTemplate: JdbcTemplate) {
  private val json = Json { ignoreUnknownKeys = true }

  fun load(
    tenantId: UUID,
    issueIds: List<UUID>,
  ): Map<UUID, Map<String, WorkItemPropertyPresentation>> {
    if (issueIds.isEmpty()) return emptyMap()
    val placeholders = issueIds.joinToString(",") { "?" }
    val rows =
      jdbcTemplate.query(
        SQL.replace(ISSUE_ID_PLACEHOLDER, placeholders),
        (listOf(tenantId) + issueIds).toPreparedStatementSetter(),
      ) { rs, _ ->
        rs.toPresentationRow()
      }
    return rows.groupBy(PresentationRow::issueId).mapValues { (_, values) ->
      values.associate { it.presentation.property.id to it.presentation }
    }
  }

  private fun ResultSet.toPresentationRow(): PresentationRow =
    PresentationRow(
      issueId = getObject("issue_id", UUID::class.java),
      presentation =
        WorkItemPropertyPresentation(
          property =
            WorkItemPropertySummary(
              id = getString("property_api_id"),
              code = getString("property_code"),
              name = getString("property_name"),
              dataType = getString("property_data_type"),
              array = getBoolean("property_is_array"),
            ),
          value = parseJson(getString("canonical_value")),
          displayValue = parseJson(getString("display_value")),
        ),
    )

  private fun parseJson(value: String): JsonElement = json.parseToJsonElement(value)

  private data class PresentationRow(
    val issueId: UUID,
    val presentation: WorkItemPropertyPresentation,
  )

  private companion object {
    private const val ISSUE_ID_PLACEHOLDER = "/* issue_ids */"
    private const val SQL =
      """
      SELECT
        ipv.issue_id,
        pd.api_id AS property_api_id,
        pd.code AS property_code,
        pd.name AS property_name,
        pd.data_type AS property_data_type,
        pd.is_array AS property_is_array,
        CASE
          WHEN ipv.value_text IS NOT NULL THEN to_jsonb(ipv.value_text)
          WHEN ipv.value_number IS NOT NULL THEN to_jsonb(ipv.value_number)
          WHEN ipv.value_boolean IS NOT NULL THEN to_jsonb(ipv.value_boolean)
          WHEN ipv.value_date IS NOT NULL THEN to_jsonb(ipv.value_date)
          WHEN ipv.value_datetime IS NOT NULL THEN to_jsonb(ipv.value_datetime)
          WHEN ipv.value_json IS NOT NULL THEN ipv.value_json
          WHEN ipv.value_user_id IS NOT NULL THEN to_jsonb(user_value.api_id)
          WHEN ipv.value_project_id IS NOT NULL THEN to_jsonb(project_value.api_id)
          WHEN ipv.value_issue_id IS NOT NULL THEN to_jsonb(issue_value.api_id)
          WHEN ipv.value_option_id IS NOT NULL THEN to_jsonb(option_value.api_id)
          WHEN ipv.value_array IS NOT NULL THEN ipv.value_array
          ELSE 'null'::jsonb
        END AS canonical_value,
        CASE pd.data_type
          WHEN 'single_select' THEN CASE
            WHEN ipv.value_option_id IS NULL THEN 'null'::jsonb
            ELSE jsonb_build_object(
              'id', option_value.api_id,
              'code', option_value.code,
              'label', option_value.label,
              'color', option_value.color
            )
          END
          WHEN 'multi_select' THEN CASE
            WHEN ipv.value_array IS NULL THEN 'null'::jsonb
            ELSE COALESCE((
            SELECT jsonb_agg(
              jsonb_build_object(
                'id', selected.api_id,
                'code', selected.code,
                'label', selected.label,
                'color', selected.color
              ) ORDER BY element.ordinality
            )
            FROM jsonb_array_elements_text(ipv.value_array) WITH ORDINALITY element(value, ordinality)
            JOIN property_options selected
              ON selected.tenant_id = ipv.tenant_id AND selected.api_id = element.value
            ), '[]'::jsonb)
          END
          WHEN 'user' THEN CASE
            WHEN ipv.value_user_id IS NULL THEN 'null'::jsonb
            ELSE jsonb_build_object(
              'id', user_value.api_id,
              'displayName', user_value.display_name
            )
          END
          WHEN 'multi_user' THEN CASE
            WHEN ipv.value_array IS NULL THEN 'null'::jsonb
            ELSE COALESCE((
            SELECT jsonb_agg(
              jsonb_build_object('id', selected.api_id, 'displayName', selected.display_name)
              ORDER BY element.ordinality
            )
            FROM jsonb_array_elements_text(ipv.value_array) WITH ORDINALITY element(value, ordinality)
            JOIN users selected ON selected.api_id = element.value
            ), '[]'::jsonb)
          END
          WHEN 'project' THEN CASE
            WHEN ipv.value_project_id IS NULL THEN 'null'::jsonb
            ELSE jsonb_build_object(
              'id', project_value.api_id,
              'identifier', project_value.identifier,
              'name', project_value.name
            )
          END
          WHEN 'issue' THEN CASE
            WHEN ipv.value_issue_id IS NULL THEN 'null'::jsonb
            ELSE jsonb_build_object(
              'id', issue_value.api_id,
              'key', COALESCE(issue_key.issue_key, issue_project.identifier || '-' || issue_value.sequence_no),
              'title', issue_value.title
            )
          END
          ELSE CASE
            WHEN ipv.value_text IS NOT NULL THEN to_jsonb(ipv.value_text)
            WHEN ipv.value_number IS NOT NULL THEN to_jsonb(ipv.value_number)
            WHEN ipv.value_boolean IS NOT NULL THEN to_jsonb(ipv.value_boolean)
            WHEN ipv.value_date IS NOT NULL THEN to_jsonb(ipv.value_date)
            WHEN ipv.value_datetime IS NOT NULL THEN to_jsonb(ipv.value_datetime)
            WHEN ipv.value_json IS NOT NULL THEN ipv.value_json
            WHEN ipv.value_array IS NOT NULL THEN ipv.value_array
            ELSE 'null'::jsonb
          END
        END AS display_value
      FROM issue_property_values ipv
      JOIN property_definitions pd ON pd.id = ipv.property_id
      LEFT JOIN property_options option_value ON option_value.id = ipv.value_option_id
      LEFT JOIN users user_value ON user_value.id = ipv.value_user_id
      LEFT JOIN projects project_value ON project_value.id = ipv.value_project_id
      LEFT JOIN issues issue_value ON issue_value.id = ipv.value_issue_id
      LEFT JOIN projects issue_project ON issue_project.id = issue_value.project_id
      LEFT JOIN issue_key_aliases issue_key
        ON issue_key.issue_id = issue_value.id AND issue_key.is_current = true
      WHERE ipv.tenant_id = ? AND ipv.issue_id IN (/* issue_ids */)
      ORDER BY ipv.issue_id, pd.api_id
      """
  }
}
