package ink.doa.workbench.data.migration

import ink.doa.workbench.core.workitem.richtext.RichTextProcessor
import java.sql.Connection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.postgresql.util.PGobject

@Suppress("ClassNaming")
class V24__BackfillDescriptionPlainTextAndCreateFieldsDescription : BaseJavaMigration() {
  private val json = Json { ignoreUnknownKeys = true }

  override fun migrate(context: Context) {
    val connection = context.connection
    backfillDescriptionPlainText(connection)
    appendDescriptionToCreateFields(connection)
  }

  private fun backfillDescriptionPlainText(connection: Connection) {
    connection
      .prepareStatement(
        """
        SELECT id, description
        FROM issues
        WHERE description IS NOT NULL
          AND description_plain_text IS NULL
        """
          .trimIndent()
      )
      .use { select ->
        select.executeQuery().use { rows ->
          connection
            .prepareStatement(
              """
              UPDATE issues
              SET description_plain_text = ?
              WHERE id = ?
              """
                .trimIndent()
            )
            .use { update ->
              while (rows.next()) {
                val description = rows.getString("description")
                val plainText =
                  RichTextProcessor.processDescription(description)?.plainText ?: description
                update.setString(1, plainText)
                update.setObject(2, rows.getObject("id"))
                update.executeUpdate()
              }
            }
        }
      }
  }

  private fun appendDescriptionToCreateFields(connection: Connection) {
    connection.prepareStatement("SELECT id, create_fields FROM issue_type_configs").use { select ->
      select.executeQuery().use { rows ->
        while (rows.next()) {
          updateCreateFieldsDescription(connection, rows)
        }
      }
    }
  }

  private fun updateCreateFieldsDescription(connection: Connection, rows: java.sql.ResultSet) {
    val createFields = json.parseToJsonElement(rows.getString("create_fields")).jsonObject
    if ("description" in createFields["fields"]!!.jsonObject) return
    val migrated = appendDescriptionField(createFields)
    connection
      .prepareStatement("UPDATE issue_type_configs SET create_fields = ? WHERE id = ?")
      .use { update ->
        update.setObject(1, jsonb(migrated))
        update.setObject(2, rows.getObject("id"))
        update.executeUpdate()
      }
  }

  private fun appendDescriptionField(createFields: JsonObject): JsonObject {
    val fields = createFields["fields"]!!.jsonObject.toMutableMap()
    fields["description"] =
      json.parseToJsonElement("""{"participation":"optional","writeGrant":"inherit"}""")
    return buildJsonObject {
      createFields.forEach { (key, value) ->
        if (key == "fields") {
          put(key, JsonObject(fields))
        } else {
          put(key, value)
        }
      }
    }
  }

  private fun jsonb(value: JsonObject): PGobject =
    PGobject().apply {
      type = "jsonb"
      this.value = value.toString()
    }
}
