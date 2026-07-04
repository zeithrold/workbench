package ink.doa.workbench.data.migration

import ink.doa.workbench.core.workitem.template.CreateFieldsLegacyMigrator
import ink.doa.workbench.core.workitem.template.CreateFieldsPropertyMigrationRow
import java.sql.Connection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.postgresql.util.PGobject

@Suppress("ClassNaming")
class V20__CreateFieldsAndGlobalTransitions : BaseJavaMigration() {
  private val json = Json { ignoreUnknownKeys = true }

  override fun migrate(context: Context) {
    val connection = context.connection
    addCreateFieldsColumn(connection)
    backfillCreateFields(connection)
    finalizeCreateFieldsColumn(connection)
    allowGlobalTransitions(connection)
  }

  private fun addCreateFieldsColumn(connection: Connection) {
    connection.createStatement().use { statement ->
      statement.execute(
        """
        ALTER TABLE issue_type_configs
          ADD COLUMN IF NOT EXISTS create_fields JSONB
        """
          .trimIndent()
      )
    }
  }

  private fun backfillCreateFields(connection: Connection) {
    connection
      .prepareStatement(
        """
        SELECT
          c.id AS config_id,
          p.code AS property_code,
          cp.is_required AS is_required,
          cp.default_value AS default_value
        FROM issue_type_configs c
        LEFT JOIN issue_type_config_properties cp ON cp.issue_type_config_id = c.id
        LEFT JOIN property_definitions p ON p.id = cp.property_id
        ORDER BY c.id, cp.rank, p.code
        """
          .trimIndent()
      )
      .use { select ->
        select.executeQuery().use { rows ->
          var currentConfigId: Any? = null
          val properties = mutableListOf<CreateFieldsPropertyMigrationRow>()
          while (rows.next()) {
            val configId = rows.getObject("config_id")
            if (currentConfigId != null && configId != currentConfigId) {
              updateCreateFields(connection, currentConfigId, properties)
              properties.clear()
            }
            currentConfigId = configId
            val propertyCode = rows.getString("property_code")
            if (propertyCode != null) {
              properties +=
                CreateFieldsPropertyMigrationRow(
                  code = propertyCode,
                  isRequired = rows.getBoolean("is_required"),
                  defaultValue =
                    rows.getString("default_value")?.let {
                      json.parseToJsonElement(it)
                    },
                )
            }
          }
          if (currentConfigId != null) {
            updateCreateFields(connection, currentConfigId, properties)
          }
        }
      }
  }

  private fun updateCreateFields(
    connection: Connection,
    configId: Any,
    properties: List<CreateFieldsPropertyMigrationRow>,
  ) {
    val migrated = CreateFieldsLegacyMigrator.migrate(properties)
    connection
      .prepareStatement("UPDATE issue_type_configs SET create_fields = ? WHERE id = ?")
      .use { update ->
        update.setObject(1, jsonb(migrated))
        update.setObject(2, configId)
        update.executeUpdate()
      }
  }

  private fun finalizeCreateFieldsColumn(connection: Connection) {
    connection.createStatement().use { statement ->
      statement.execute(
        """
        ALTER TABLE issue_type_configs
          ALTER COLUMN create_fields SET NOT NULL,
          ALTER COLUMN create_fields SET DEFAULT '{"version":1,"resource":"work_item","target":"create","fields":{"title":{"participation":"required"}}}'::jsonb
        """
          .trimIndent()
      )
      statement.execute(
        """
        ALTER TABLE issue_type_config_properties
          DROP COLUMN is_required,
          DROP COLUMN default_value
        """
          .trimIndent()
      )
    }
  }

  private fun allowGlobalTransitions(connection: Connection) {
    connection.createStatement().use { statement ->
      statement.execute(
        """
        ALTER TABLE workflow_transitions
          ALTER COLUMN from_status_id DROP NOT NULL
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
}
