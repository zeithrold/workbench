package ink.doa.workbench.data

import ink.doa.workbench.testsupport.postgres.MigrationSpec
import ink.doa.workbench.testsupport.postgres.WorkbenchPostgresTestSupport
import ink.doa.workbench.testsupport.postgres.jdbcTemplate
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate

@Tags("integration")
class PostgresMigrationIntegrationTest :
  StringSpec({
    "Flyway migrations run on PostgreSQL" {
      withMigratedPostgres { _, result ->
        result.migrationsExecuted shouldBe 46
      }
    }

    "V26 backfills comment html and plain text" {
      WorkbenchPostgresTestSupport.openDatabase(
          WorkbenchPostgresTestSupport.customMigration(configure = { target("24") })
        )
        .use { lease ->
          val jdbc = lease.jdbcTemplate()
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

          org.flywaydb.core.Flyway.configure()
            .dataSource(lease.jdbcUrl, lease.username, lease.password)
            .locations(*MigrationSpec.Full.locations())
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

    "V44 migrates workflow transition permissions into access rules" {
      WorkbenchPostgresTestSupport.openDatabase(
          WorkbenchPostgresTestSupport.customMigration(configure = { target("43") })
        )
        .use { lease ->
          val jdbc = lease.jdbcTemplate()
          val seed = seedTransitionPermissionFixture(jdbc)
          org.flywaydb.core.Flyway.configure()
            .dataSource(lease.jdbcUrl, lease.username, lease.password)
            .locations(*MigrationSpec.Full.locations())
            .target("44")
            .load()
            .migrate()

          val migratedRuleCount =
            jdbc.queryForObject(
              """
              SELECT COUNT(*)
              FROM issue_type_config_access_rules
              WHERE tenant_id = ? AND transition_id = ? AND effect = 'deny'
              """
                .trimIndent(),
              Int::class.java,
              seed.tenantId,
              seed.transitionId,
            )
          migratedRuleCount shouldBe 1

          val clearedPermission =
            jdbc.queryForObject(
              """
              SELECT permission_condition::text
              FROM workflow_transitions
              WHERE id = ?
              """
                .trimIndent(),
              String::class.java,
              seed.transitionId,
            )
          clearedPermission shouldBe "{}"
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

private data class TransitionPermissionFixtureSeed(
  val tenantId: UUID,
  val transitionId: UUID,
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

private fun seedTransitionPermissionFixture(jdbc: JdbcTemplate): TransitionPermissionFixtureSeed {
  val tenantId = UUID.randomUUID()
  val workflowId = UUID.randomUUID()
  val issueTypeId = UUID.randomUUID()
  val issueTypeConfigId = UUID.randomUUID()
  val fromStatusId = UUID.randomUUID()
  val toStatusId = UUID.randomUUID()
  val transitionId = UUID.randomUUID()
  jdbc.update(
    "INSERT INTO tenants (id, api_id, name, slug) VALUES (?, ?, ?, ?)",
    tenantId,
    "ten_${tenantId.toString().replace("-", "").take(12)}",
    "Tenant",
    "tenant-${tenantId.toString().take(8)}",
  )
  jdbc.update(
    """
    INSERT INTO issue_statuses (id, api_id, tenant_id, code, name, status_group)
    VALUES (?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?)
    """
      .trimIndent(),
    fromStatusId,
    "sts_${fromStatusId.toString().replace("-", "").take(12)}",
    tenantId,
    "todo",
    "Todo",
    "todo",
    toStatusId,
    "sts_${toStatusId.toString().replace("-", "").take(12)}",
    tenantId,
    "done",
    "Done",
    "done",
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
    INSERT INTO workflow_transitions (
      id, api_id, tenant_id, workflow_id, name, from_status_id, to_status_id,
      permission_condition, precondition_ast, fields
    )
    VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, '{}'::jsonb, '{}'::jsonb)
    """
      .trimIndent(),
    transitionId,
    "wtr_${transitionId.toString().replace("-", "").take(12)}",
    tenantId,
    workflowId,
    "Start",
    fromStatusId,
    toStatusId,
    """{"field":"issue.statusGroup","op":"eq","value":"todo"}""",
  )
  return TransitionPermissionFixtureSeed(tenantId = tenantId, transitionId = transitionId)
}

private fun withMigratedPostgres(
  target: String? = null,
  block: (JdbcTemplate, org.flywaydb.core.api.output.MigrateResult) -> Unit,
) {
  WorkbenchPostgresTestSupport.openDatabase(
      WorkbenchPostgresTestSupport.customMigration(
        migrateOnOpen = false,
        configure = {
          if (target != null) {
            target(target)
          } else {
            this
          }
        },
      )
    )
    .use { lease ->
      val jdbc = lease.jdbcTemplate()
      val result =
        org.flywaydb.core.Flyway.configure()
          .dataSource(lease.jdbcUrl, lease.username, lease.password)
          .locations(*MigrationSpec.Full.locations())
          .apply { if (target != null) target(target) }
          .load()
          .migrate()
      block(jdbc, result)
    }
}
