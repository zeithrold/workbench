package ink.doa.workbench.data.messaging

import ink.doa.workbench.core.messaging.OutboxMessageQuery
import ink.doa.workbench.testsupport.postgres.MigrationSpec
import ink.doa.workbench.testsupport.postgres.WorkbenchPostgresTestSupport
import ink.doa.workbench.testsupport.postgres.jdbcTemplate
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.util.UUID

@Tags("integration")
class ExposedOutboxAdminStoreIntegrationTest :
  StringSpec({
    fun insertMessage(
      jdbc: org.springframework.jdbc.core.JdbcTemplate,
      id: UUID,
      status: String,
      tenantId: String = "ten_1",
      eventType: String = "work_item.updated",
    ) {
      jdbc.update(
        """
        INSERT INTO domain_outbox (
          id, event_id, event_type, event_version, topic, partition_key, tenant_id, payload,
          status, created_at, updated_at, next_attempt_at, attempts
        )
        VALUES (?, ?, ?, 1, 'workbench.work-item', 'wki_1', ?, '{}'::jsonb,
                ?, now(), now(), now(), 0)
        """
          .trimIndent(),
        id,
        "evt_$id",
        eventType,
        tenantId,
        status,
      )
    }

    "list filters by status tenant and event type" {
      WorkbenchPostgresTestSupport.openDatabase(MigrationSpec.Full).use { lease ->
        val jdbc = lease.jdbcTemplate()
        val store = ExposedOutboxAdminStore(jdbc)
        val pendingId = UUID.randomUUID()
        val deadId = UUID.randomUUID()
        insertMessage(
          jdbc,
          pendingId,
          "PENDING",
          tenantId = "ten_a",
          eventType = "work_item.created",
        )
        insertMessage(jdbc, deadId, "DEAD", tenantId = "ten_b", eventType = "work_item.updated")

        store.list(OutboxMessageQuery(status = "DEAD")).single().id shouldBe deadId
        store.list(OutboxMessageQuery(tenantId = "ten_a")).single().tenantId shouldBe "ten_a"
        store.list(OutboxMessageQuery(eventType = "work_item.created")).single().eventType shouldBe
          "work_item.created"
        store.list(OutboxMessageQuery(limit = 1, offset = 0)) shouldHaveSize 1
        store.countByStatus("DEAD") shouldBe 1
        store.countByStatus("PENDING") shouldBe 1
      }
    }

    "findById returns record or null" {
      WorkbenchPostgresTestSupport.openDatabase(MigrationSpec.Full).use { lease ->
        val jdbc = lease.jdbcTemplate()
        val store = ExposedOutboxAdminStore(jdbc)
        val id = UUID.randomUUID()
        insertMessage(jdbc, id, "PENDING")

        store.findById(id)?.eventId shouldBe "evt_$id"
        store.findById(UUID.randomUUID()).shouldBeNull()
      }
    }

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
        store.replayDead(id) shouldBe false
      }
    }
  })
