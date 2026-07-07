package ink.doa.workbench.data.migration

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.query.WorkItemConditionJson
import java.sql.Connection
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.postgresql.util.PGobject

@Suppress("ClassNaming")
class V44__MigrateWorkflowTransitionPermissions : BaseJavaMigration() {
  private val json = Json { ignoreUnknownKeys = true }

  override fun migrate(context: Context) {
    migrateTransitionPermissions(context.connection)
    clearWorkflowTransitionPermissions(context.connection)
  }

  private fun migrateTransitionPermissions(connection: Connection) {
    connection
      .prepareStatement(
        """
        SELECT
          wt.id AS transition_id,
          wt.tenant_id,
          wt.permission_condition::text AS permission_condition,
          itc.id AS issue_type_config_id
        FROM workflow_transitions wt
        JOIN issue_type_configs itc
          ON itc.workflow_id = wt.workflow_id
         AND itc.tenant_id = wt.tenant_id
        WHERE wt.is_active = TRUE
          AND itc.is_active = TRUE
          AND itc.valid_to IS NULL
          AND wt.permission_condition IS NOT NULL
          AND wt.permission_condition::text <> '{}'
        """
          .trimIndent()
      )
      .use { select ->
        select.executeQuery().use { rows ->
          while (rows.next()) {
            insertMigratedRule(connection, rows)
          }
        }
      }
  }

  private fun insertMigratedRule(connection: Connection, rows: java.sql.ResultSet) {
    val permissionCondition =
      json.parseToJsonElement(rows.getString("permission_condition")).jsonObject
    if (WorkItemConditionJson.parse(permissionCondition) == null) {
      return
    }
    val denyCondition = negateCondition(permissionCondition)
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    connection
      .prepareStatement(
        """
        INSERT INTO issue_type_config_access_rules (
          id,
          api_id,
          tenant_id,
          issue_type_config_id,
          subject_type,
          subject_user_id,
          subject_group_id,
          subject_role_code,
          action_type,
          transition_id,
          field_key,
          effect,
          condition_json,
          rank,
          is_active,
          created_at,
          updated_at
        )
        VALUES (?, ?, ?, ?, 'anyone', NULL, NULL, NULL, 'transition', ?, NULL, 'deny', ?, 100, TRUE, ?, ?)
        """
          .trimIndent()
      )
      .use { statement ->
        statement.setObject(1, UUID.randomUUID())
        statement.setString(2, PublicId.new("war").value)
        statement.setObject(3, rows.getObject("tenant_id"))
        statement.setObject(4, rows.getObject("issue_type_config_id"))
        statement.setObject(5, rows.getObject("transition_id"))
        statement.setObject(6, jsonb(denyCondition))
        statement.setTimestamp(7, Timestamp.from(now.toInstant()))
        statement.setTimestamp(8, Timestamp.from(now.toInstant()))
        statement.executeUpdate()
      }
  }

  private fun clearWorkflowTransitionPermissions(connection: Connection) {
    connection.createStatement().use { statement ->
      statement.execute(
        """
        UPDATE workflow_transitions
        SET permission_condition = '{}'::jsonb,
            updated_at = now()
        WHERE permission_condition IS NOT NULL
          AND permission_condition::text <> '{}'
        """
          .trimIndent()
      )
    }
  }

  private fun negateCondition(condition: JsonObject): JsonObject = buildJsonObject {
    put("op", "not")
    put("arg", condition)
  }

  private fun jsonb(value: JsonObject): PGobject =
    PGobject().apply {
      type = "jsonb"
      this.value = value.toString()
    }
}
