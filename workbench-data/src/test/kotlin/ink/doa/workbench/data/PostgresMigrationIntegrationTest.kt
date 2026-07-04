@file:Suppress("LongMethod")

package ink.doa.workbench.data

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.UUID
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Tag
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer

@Tag("integration")
class PostgresMigrationIntegrationTest :
  StringSpec({
    "Flyway migrations run on PostgreSQL" {
      withMigratedPostgres { _, result ->
        result.migrationsExecuted shouldBe 25
      }
    }

    "V26 backfills comment html and plain text" {
      PostgreSQLContainer("postgres:18-alpine").use { postgres ->
        postgres.start()
        val jdbcUrl = postgres.jdbcUrl
        val username = postgres.username
        val password = postgres.password

        Flyway.configure()
          .dataSource(jdbcUrl, username, password)
          .locations("classpath:db/migration", "classpath:ink/doa/workbench/data/migration")
          .target("24")
          .load()
          .migrate()

        val jdbc =
          JdbcTemplate(
            DriverManagerDataSource(jdbcUrl, username, password).apply {
              setDriverClassName("org.postgresql.Driver")
            }
          )
        val seed = seedCommentFixture(jdbc)
        jdbc.update(
          """
          INSERT INTO issue_comments (
            id, api_id, tenant_id, issue_id, author_id, body, body_format, created_at, updated_at
          )
          VALUES (?, ?, ?, ?, ?, ?, ?, now(), now())
          """
            .trimIndent(),
          seed.commentId,
          seed.commentApiId,
          seed.tenantId,
          seed.issueId,
          seed.userId,
          "Legacy plain comment",
          "plain_text",
        )

        Flyway.configure()
          .dataSource(jdbcUrl, username, password)
          .locations("classpath:db/migration", "classpath:ink/doa/workbench/data/migration")
          .load()
          .migrate()

        val row =
          jdbc.queryForMap(
            """
            SELECT body, body_plain_text, body_format
            FROM issue_comments
            WHERE id = ?
            """
              .trimIndent(),
            seed.commentId,
          )
        row["body"] shouldBe "<p>Legacy plain comment</p>"
        row["body_plain_text"] shouldBe "Legacy plain comment"
        row["body_format"] shouldBe "html"
      }
    }
  })

private data class CommentFixtureSeed(
  val tenantId: UUID,
  val userId: UUID,
  val issueId: UUID,
  val commentId: UUID,
  val commentApiId: String,
)

private fun seedCommentFixture(jdbc: JdbcTemplate): CommentFixtureSeed {
  val tenantId = UUID.randomUUID()
  val userId = UUID.randomUUID()
  val projectId = UUID.randomUUID()
  val statusId = UUID.randomUUID()
  val workflowId = UUID.randomUUID()
  val issueTypeId = UUID.randomUUID()
  val issueTypeConfigId = UUID.randomUUID()
  val issueId = UUID.randomUUID()
  val commentId = UUID.randomUUID()
  jdbc.update(
    "INSERT INTO tenants (id, api_id, name, slug) VALUES (?, ?, ?, ?)",
    tenantId,
    "ten_${tenantId.toString().replace("-", "").take(12)}",
    "Tenant",
    "tenant-${tenantId.toString().take(8)}",
  )
  jdbc.update(
    "INSERT INTO users (id, api_id, display_name) VALUES (?, ?, ?)",
    userId,
    "usr_${userId.toString().replace("-", "").take(12)}",
    "Ada",
  )
  jdbc.update(
    "INSERT INTO projects (id, api_id, tenant_id, name, identifier) VALUES (?, ?, ?, ?, ?)",
    projectId,
    "prj_${projectId.toString().replace("-", "").take(12)}",
    tenantId,
    "Core",
    "CORE",
  )
  jdbc.update(
    """
    INSERT INTO issue_statuses (id, api_id, tenant_id, code, name, status_group)
    VALUES (?, ?, ?, ?, ?, ?)
    """
      .trimIndent(),
    statusId,
    "sts_${statusId.toString().replace("-", "").take(12)}",
    tenantId,
    "todo",
    "Todo",
    "todo",
  )
  jdbc.update(
    "INSERT INTO workflows (id, api_id, tenant_id, code, name) VALUES (?, ?, ?, ?, ?)",
    workflowId,
    "wfl_${workflowId.toString().replace("-", "").take(12)}",
    tenantId,
    "default",
    "Default",
  )
  jdbc.update(
    """
    INSERT INTO issue_types (id, api_id, tenant_id, scope, code, name)
    VALUES (?, ?, ?, ?, ?, ?)
    """
      .trimIndent(),
    issueTypeId,
    "typ_${issueTypeId.toString().replace("-", "").take(12)}",
    tenantId,
    "tenant",
    "task",
    "Task",
  )
  jdbc.update(
    """
    INSERT INTO issue_type_configs (id, api_id, tenant_id, scope, issue_type_id, workflow_id)
    VALUES (?, ?, ?, ?, ?, ?)
    """
      .trimIndent(),
    issueTypeConfigId,
    "itc_${issueTypeConfigId.toString().replace("-", "").take(12)}",
    tenantId,
    "tenant",
    issueTypeId,
    workflowId,
  )
  jdbc.update(
    """
    INSERT INTO issues (
      id, api_id, tenant_id, project_id, issue_type_id, issue_type_config_id,
      sequence_no, title, status_id, reporter_id, created_by
    )
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """
      .trimIndent(),
    issueId,
    "iss_${issueId.toString().replace("-", "").take(12)}",
    tenantId,
    projectId,
    issueTypeId,
    issueTypeConfigId,
    1L,
    "Issue",
    statusId,
    userId,
    userId,
  )
  return CommentFixtureSeed(
    tenantId = tenantId,
    userId = userId,
    issueId = issueId,
    commentId = commentId,
    commentApiId = "icm_${commentId.toString().replace("-", "").take(12)}",
  )
}

private fun withMigratedPostgres(
  target: String? = null,
  block: (JdbcTemplate, org.flywaydb.core.api.output.MigrateResult) -> Unit,
) {
  PostgreSQLContainer("postgres:18-alpine").use { postgres ->
    postgres.start()
    val flyway =
      Flyway.configure()
        .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        .locations("classpath:db/migration", "classpath:ink/doa/workbench/data/migration")
        .apply { if (target != null) target(target) }
        .load()
    val result = flyway.migrate()
    val dataSource =
      DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password).apply {
        setDriverClassName("org.postgresql.Driver")
      }
    block(JdbcTemplate(dataSource), result)
  }
}
