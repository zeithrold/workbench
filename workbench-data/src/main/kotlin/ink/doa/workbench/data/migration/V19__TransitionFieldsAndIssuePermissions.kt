package ink.doa.workbench.data.migration

import ink.doa.workbench.core.workitem.template.TransitionFieldsLegacyMigrator
import java.sql.Connection
import java.sql.Statement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.postgresql.util.PGobject

@Suppress("ClassNaming")
class V19__TransitionFieldsAndIssuePermissions : BaseJavaMigration() {
  private val json = Json { ignoreUnknownKeys = true }

  override fun migrate(context: Context) {
    val connection = context.connection
    bootstrapPermissions(connection)
    addFieldsColumn(connection)
    migrateLegacyTransitionFields(connection)
    finalizeFieldsColumn(connection)
  }

  private fun bootstrapPermissions(connection: Connection) {
    connection.createStatement().use { statement ->
      statement.execute(
        """
        INSERT INTO permission_actions (id, code, description)
        VALUES (gen_random_uuid(), 'issue.field.write', 'Write a specific work item field.')
        ON CONFLICT (code) DO NOTHING
        """
          .trimIndent()
      )

      insertPolicyRules(statement, "tenant-admin", ISSUE_WRITE_RULES)
      insertPolicyRules(statement, "project-admin", ISSUE_WRITE_RULES)
      insertPolicyRules(statement, "project-member", ISSUE_WRITE_RULES)
      insertPolicyRules(statement, "project-viewer", ISSUE_VIEW_RULES)
    }
  }

  private fun addFieldsColumn(connection: Connection) {
    connection.createStatement().use { statement ->
      statement.execute(
        """
        ALTER TABLE workflow_transitions
          ADD COLUMN IF NOT EXISTS fields JSONB
        """
          .trimIndent()
      )
    }
  }

  private fun migrateLegacyTransitionFields(connection: Connection) {
    connection
      .prepareStatement(
        """
        SELECT id, required_properties, optional_properties, property_defaults
        FROM workflow_transitions
        """
          .trimIndent()
      )
      .use { select ->
        select.executeQuery().use { rows ->
          while (rows.next()) {
            updateTransitionFields(connection, rows)
          }
        }
      }
  }

  private fun updateTransitionFields(connection: Connection, rows: java.sql.ResultSet) {
    val id = rows.getObject("id")
    val required = rows.getString("required_properties")
    val optional = rows.getString("optional_properties")
    val defaults = rows.getString("property_defaults")
    val migrated =
      TransitionFieldsLegacyMigrator.migrate(
        requiredProperties = json.parseToJsonElement(required),
        optionalProperties = json.parseToJsonElement(optional),
        propertyDefaults = json.parseToJsonElement(defaults).jsonObject,
      )
    connection.prepareStatement("UPDATE workflow_transitions SET fields = ? WHERE id = ?").use {
      update ->
      update.setObject(1, jsonb(migrated))
      update.setObject(2, id)
      update.executeUpdate()
    }
  }

  private fun finalizeFieldsColumn(connection: Connection) {
    connection.createStatement().use { statement ->
      statement.execute(
        """
        ALTER TABLE workflow_transitions
          ALTER COLUMN fields SET NOT NULL,
          ALTER COLUMN fields SET DEFAULT '{"version":1,"resource":"work_item","target":"transition","fields":{}}'::jsonb
        """
          .trimIndent()
      )
      statement.execute(
        """
        ALTER TABLE workflow_transitions
          DROP COLUMN required_properties,
          DROP COLUMN optional_properties,
          DROP COLUMN property_defaults
        """
          .trimIndent()
      )
    }
  }

  private fun insertPolicyRules(
    statement: Statement,
    policyCode: String,
    rules: List<Pair<String, String>>,
  ) {
    rules.forEach { (action, resourcePattern) ->
      statement.execute(
        """
        INSERT INTO permission_policy_rules (
          id, api_id, policy_id, action, resource_pattern, effect, condition_json, created_at
        )
        SELECT
          gen_random_uuid(),
          'prl_' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 26)),
          pp.id,
          '${action.replace("'", "''")}',
          '${resourcePattern.replace("'", "''")}',
          'allow',
          NULL,
          now()
        FROM permission_policies pp
        WHERE pp.code = '${policyCode.replace("'", "''")}'
          AND NOT EXISTS (
            SELECT 1
            FROM permission_policy_rules existing
            WHERE existing.policy_id = pp.id
              AND existing.action = '${action.replace("'", "''")}'
              AND existing.resource_pattern = '${resourcePattern.replace("'", "''")}'
          )
        """
          .trimIndent()
      )
    }
  }

  private fun jsonb(value: JsonObject): PGobject =
    PGobject().apply {
      type = "jsonb"
      this.value = value.toString()
    }

  private companion object {
    val ISSUE_VIEW_RULES = listOf("issue.view" to "issue:*")
    val ISSUE_WRITE_RULES =
      listOf(
        "issue.view" to "issue:*",
        "issue.create" to "issue:*",
        "issue.update" to "issue:*",
        "issue.transition" to "issue:*",
        "issue.field.write" to "issue:field:*",
      )
  }
}
