package ink.doa.workbench.data.messaging

import ink.doa.workbench.application.messaging.OutboxMessageQuery
import ink.doa.workbench.testsupport.postgres.MigrationSpec
import ink.doa.workbench.testsupport.postgres.WorkbenchPostgresTestSupport
import ink.doa.workbench.testsupport.postgres.jdbcTemplate
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.util.UUID

class ExposedOutboxAdminStoreIntegrationTest :
  StringSpec({
    fun insertMessage(
      jdbc: org.springframework.jdbc.core.JdbcTemplate,
      id: UUID,
      tenantId: String = "ten_1",
      eventType: String = "work_item.updated",
    ) {
      jdbc.update(
        """
        INSERT INTO domain_outbox (
          id, event_id, event_type, event_version, topic, partition_key, tenant_id, payload,
          created_at, retention_until
        )
        VALUES (?, ?, ?, 1, 'workbench.work-item', 'wki_1', ?, '{}'::jsonb,
                now(), now() + interval '30 days')
        """
          .trimIndent(),
        id,
        "evt_$id",
        eventType,
        tenantId,
      )
    }

    "list filters immutable events by tenant and event type" {
      WorkbenchPostgresTestSupport.openDatabase(MigrationSpec.Full).use { lease ->
        val jdbc = lease.jdbcTemplate()
        val store = ExposedOutboxAdminStore(jdbc)
        val createdId = UUID.randomUUID()
        insertMessage(jdbc, createdId, tenantId = "ten_a", eventType = "work_item.created")
        insertMessage(
          jdbc,
          UUID.randomUUID(),
          tenantId = "ten_b",
          eventType = "work_item.updated",
        )

        store.list(OutboxMessageQuery(tenantId = "ten_a")).single().tenantId shouldBe "ten_a"
        store.list(OutboxMessageQuery(eventType = "work_item.created")).single().id shouldBe
          createdId
        store.list(OutboxMessageQuery(limit = 1, offset = 0)) shouldHaveSize 1
      }
    }

    "findById returns immutable record or null" {
      WorkbenchPostgresTestSupport.openDatabase(MigrationSpec.Full).use { lease ->
        val jdbc = lease.jdbcTemplate()
        val store = ExposedOutboxAdminStore(jdbc)
        val id = UUID.randomUUID()
        insertMessage(jdbc, id)

        store.findById(id)?.eventId shouldBe "evt_$id"
        store.findById(id)?.retentionUntil shouldBe store.findById(id)?.createdAt?.plusDays(30)
        store.findById(UUID.randomUUID()).shouldBeNull()
      }
    }
  })
