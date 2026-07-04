package ink.doa.workbench.data.migration

import ink.doa.workbench.core.workitem.richtext.RichTextProcessor
import java.sql.Connection
import java.util.logging.Logger
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

@Suppress("ClassNaming")
class V26__BackfillCommentHtmlAndPlainText : BaseJavaMigration() {
  private val logger = Logger.getLogger(V26__BackfillCommentHtmlAndPlainText::class.java.name)

  override fun migrate(context: Context) {
    backfillCommentBodies(context.connection)
  }

  private fun backfillCommentBodies(connection: Connection) {
    connection
      .prepareStatement(
        """
        SELECT id, body
        FROM issue_comments
        WHERE deleted_at IS NULL
        """
          .trimIndent()
      )
      .use { select ->
        select.executeQuery().use { rows ->
          while (rows.next()) {
            updateCommentBody(connection, rows)
          }
        }
      }
  }

  private fun updateCommentBody(connection: Connection, rows: java.sql.ResultSet) {
    val id = rows.getObject("id")
    val originalBody = rows.getString("body")
    val processed = RichTextProcessor.processCommentInput(originalBody)
    if (processed?.html.isNullOrBlank()) {
      logger.warning("Skipping comment $id: sanitized body is empty; preserving original body.")
      return
    }
    connection
      .prepareStatement(
        """
        UPDATE issue_comments
        SET body = ?,
            body_plain_text = ?,
            body_format = 'html'
        WHERE id = ?
        """
          .trimIndent()
      )
      .use { update ->
        update.setString(1, processed.html)
        update.setString(2, processed.plainText)
        update.setObject(3, id)
        update.executeUpdate()
      }
  }
}
