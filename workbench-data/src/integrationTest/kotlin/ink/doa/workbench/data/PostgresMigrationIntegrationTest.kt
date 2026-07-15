package ink.doa.workbench.data

import ink.doa.workbench.testsupport.postgres.MigrationSpec
import ink.doa.workbench.testsupport.postgres.WorkbenchPostgresTestSupport
import ink.doa.workbench.testsupport.postgres.jdbcTemplate
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import org.flywaydb.core.Flyway

class PostgresMigrationIntegrationTest :
  StringSpec({
    "flattened baseline creates the complete PostgreSQL schema" {
      withMigratedPostgres { jdbc, result, _ ->
        result.migrationsExecuted shouldBe 5

        jdbc.queryForObject(
          """
          SELECT COUNT(*)
          FROM information_schema.tables
          WHERE table_schema = 'public'
            AND table_name IN (
              'tenants', 'projects', 'issues', 'work_item_events', 'domain_outbox',
              'domain_event_deliveries', 'outbox_transport_publications', 'notifications'
            )
          """
            .trimIndent(),
          Int::class.java,
        ) shouldBe 8
        jdbc.queryForObject(
          "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'issue_type_configs' AND column_name = 'create_fields'",
          Int::class.java,
        ) shouldBe 1
        jdbc.queryForObject(
          "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'workflow_transitions' AND column_name = 'permission_condition'",
          Int::class.java,
        ) shouldBe 0
        jdbc.queryForObject(
          "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'domain_outbox' AND column_name IN ('status', 'attempts', 'published_at', 'retention_until')",
          Int::class.java,
        ) shouldBe 1
      }
    }

    "flattened baseline seeds login methods and permission actions" {
      withMigratedPostgres { jdbc, _, _ ->
        jdbc.queryForList(
          "SELECT code FROM login_method_definitions ORDER BY code",
          String::class.java,
        ) shouldBe listOf("instance_password", "password")

        val actionCodes =
          jdbc.queryForList("SELECT code FROM permission_actions", String::class.java)
        actionCodes.size shouldBe 46
        actionCodes shouldContainAll
          listOf(
            "tenant.access",
            "issue.transition",
            "workitem.config.manage",
            "sprint.workitem.disposition",
            "notification.manage",
            "outbox.manage",
            "instance.read",
            "instance.admin.manage",
            "operations.read",
          )
      }
    }

    "flattened baseline is idempotent through Flyway history" {
      withMigratedPostgres { _, _, flyway ->
        flyway.migrate().migrationsExecuted shouldBe 0
        flyway.info().applied().size shouldBe 5
      }
    }
  })

private fun withMigratedPostgres(
  block:
    (
      org.springframework.jdbc.core.JdbcTemplate,
      org.flywaydb.core.api.output.MigrateResult,
      Flyway,
    ) -> Unit
) {
  WorkbenchPostgresTestSupport.openDatabase(
      WorkbenchPostgresTestSupport.customMigration(migrateOnOpen = false)
    )
    .use { lease ->
      val flyway =
        Flyway.configure()
          .dataSource(lease.jdbcUrl, lease.username, lease.password)
          .locations(*MigrationSpec.Full.locations())
          .load()
      val result = flyway.migrate()
      block(lease.jdbcTemplate(), result, flyway)
    }
}
