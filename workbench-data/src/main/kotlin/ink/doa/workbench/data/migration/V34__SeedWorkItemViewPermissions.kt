package ink.doa.workbench.data.migration

import java.sql.Statement
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

@Suppress("ClassNaming")
class V34__SeedWorkItemViewPermissions : BaseJavaMigration() {
  override fun migrate(context: Context) {
    val connection = context.connection
    connection.createStatement().use { statement ->
      statement.execute(
        """
        INSERT INTO permission_actions (id, code, description)
        VALUES
            (gen_random_uuid(), 'view.read', 'Read work item views.'),
            (gen_random_uuid(), 'view.create', 'Create work item views.'),
            (gen_random_uuid(), 'view.manage', 'Manage work item views.')
        ON CONFLICT (code) DO NOTHING
        """
          .trimIndent()
      )

      insertPolicyRules(statement, "tenant-admin", TENANT_VIEW_RULES)
      insertPolicyRules(statement, "project-admin", PROJECT_VIEW_RULES)
      insertPolicyRules(statement, "project-member", PROJECT_VIEW_RULES)
      insertPolicyRules(statement, "project-viewer", PROJECT_VIEW_READ_CREATE_RULES)
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

  private companion object {
    val TENANT_VIEW_RULES =
      listOf(
        "view.read" to "view:*",
        "view.create" to "view:*",
        "view.manage" to "view:*",
      )
    val PROJECT_VIEW_RULES =
      listOf(
        "view.read" to "view:*",
        "view.create" to "view:*",
      )
    val PROJECT_VIEW_READ_CREATE_RULES =
      listOf(
        "view.read" to "view:*",
        "view.create" to "view:*",
      )
  }
}
