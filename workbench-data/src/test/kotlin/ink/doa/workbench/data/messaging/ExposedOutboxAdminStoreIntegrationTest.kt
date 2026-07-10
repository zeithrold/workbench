package ink.doa.workbench.data.messaging

import ink.doa.workbench.testsupport.postgres.MigrationSpec
import ink.doa.workbench.testsupport.postgres.WorkbenchPostgresTestSupport
import ink.doa.workbench.testsupport.postgres.jdbcTemplate
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

@Tags("integration")
class ExposedOutboxAdminStoreIntegrationTest :
  StringSpec({
    "replayDead resets dead message to retry" {
      WorkbenchPostgresTestSupport.openDatabase(MigrationSpec.Full).use { lease ->
        val jdbc = lease.jdbcTemplate()
        val store = ExposedOutboxAdminStore(jdbc)
        val id = UUID.randomUUID()
        jdbc.update(
          """
          INSERT INTO domain_outbox (
            id, event_id, event_type, event_version, topic, partition_key, payload,
            status, created_at, updated_at, next_attempt_at, attempts, last_error
          )
          VALUES (?, 'evt_dead', 'work_item.updated', 1, 'workbench.work-item', 'wki_1',
                  '{}'::jsonb, 'DEAD', now(), now(), now(), 8, 'broker down')
          """
            .trimIndent(),
          id,
        )

        store.replayDead(id) shouldBe true
        store.findById(id)?.status shouldBe "RETRY"
        store.findById(id)?.attempts shouldBe 0
      }
    }
  })
