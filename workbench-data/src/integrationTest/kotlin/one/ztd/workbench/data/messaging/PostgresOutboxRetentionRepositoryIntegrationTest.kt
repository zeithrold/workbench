package one.ztd.workbench.data.messaging

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import one.ztd.workbench.testsupport.postgres.MigrationSpec
import one.ztd.workbench.testsupport.postgres.WorkbenchPostgresTestSupport
import one.ztd.workbench.testsupport.postgres.jdbcTemplate

class PostgresOutboxRetentionRepositoryIntegrationTest :
  StringSpec({
    "cleanup deletes only expired outbox events with terminal deliveries" {
      WorkbenchPostgresTestSupport.openDatabase(MigrationSpec.Full).use { lease ->
        val jdbc = lease.jdbcTemplate()
        val repository = PostgresOutboxRetentionRepository(jdbc)
        val terminal = insertExpiredOutbox(jdbc)
        val active = insertExpiredOutbox(jdbc)
        val retained = insertExpiredOutbox(jdbc, expired = false)
        insertDelivery(jdbc, terminal, "SUCCEEDED")
        insertDelivery(jdbc, active, "RETRY")
        insertDelivery(jdbc, retained, "SUCCEEDED")

        repository.deleteExpiredTerminal(10, OffsetDateTime.now(ZoneOffset.UTC)) shouldBe 1
        jdbc.queryForObject("SELECT count(*) FROM domain_outbox", Long::class.java) shouldBe 2L
        jdbc.queryForObject(
          "SELECT count(*) FROM domain_outbox WHERE id = ?",
          Long::class.java,
          active,
        ) shouldBe 1L
      }
    }
  })

private fun insertExpiredOutbox(
  jdbc: org.springframework.jdbc.core.JdbcTemplate,
  expired: Boolean = true,
): UUID {
  val id = UUID.randomUUID()
  jdbc.update(
    """
    INSERT INTO domain_outbox (
      id, event_id, event_type, event_version, topic, partition_key, payload,
      created_at, retention_until
    ) VALUES (?, ?, 'work_item.updated', 1, 'workbench.work-item', ?, '{}'::jsonb,
              now() - interval '31 days', now() + (? * interval '1 day'))
    """
      .trimIndent(),
    id,
    "evt_$id",
    id.toString(),
    if (expired) -1 else 1,
  )
  return id
}

private fun insertDelivery(
  jdbc: org.springframework.jdbc.core.JdbcTemplate,
  outboxId: UUID,
  status: String,
) {
  jdbc.update(
    """
    INSERT INTO domain_event_deliveries (
      outbox_id, consumer_name, partition_key, status, next_attempt_at, created_at, updated_at
    ) VALUES (?, 'consumer', ?, ?, now(), now(), now())
    """
      .trimIndent(),
    outboxId,
    outboxId.toString(),
    status,
  )
}
