package ink.doa.workbench.data.messaging

import ink.doa.workbench.testsupport.postgres.MigrationSpec
import ink.doa.workbench.testsupport.postgres.WorkbenchPostgresTestSupport
import ink.doa.workbench.testsupport.postgres.jdbcTemplate
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class PostgresOperationsDataStoreIntegrationTest :
  StringSpec({
    "aggregates delivery statuses and hourly trend" {
      WorkbenchPostgresTestSupport.openDatabase(MigrationSpec.Full).use { lease ->
        val jdbc = lease.jdbcTemplate()
        val store = PostgresOperationsDataStore(jdbc)
        val bucket = OffsetDateTime.of(2026, 7, 15, 10, 20, 0, 0, ZoneOffset.UTC)
        insertDelivery(jdbc, "SUCCEEDED", bucket)
        insertDelivery(jdbc, "RETRY", bucket.plusMinutes(10))
        insertDelivery(jdbc, "DEAD", bucket.plusHours(1))

        store.deliveryStatusCounts().associate { it.status to it.count } shouldBe
          mapOf("SUCCEEDED" to 1L, "RETRY" to 1L, "DEAD" to 1L)
        store.deliveryTrendSince(bucket.minusMinutes(1)).map {
          Triple(it.bucketAt, it.succeeded, it.failed)
        } shouldBe
          listOf(
            Triple(bucket.withMinute(0), 1L, 1L),
            Triple(bucket.plusHours(1).withMinute(0), 0L, 1L),
          )
      }
    }
  })

private fun insertDelivery(
  jdbc: org.springframework.jdbc.core.JdbcTemplate,
  status: String,
  updatedAt: OffsetDateTime,
) {
  val outboxId = UUID.randomUUID()
  jdbc.update(
    """
    INSERT INTO domain_outbox (
      id, event_id, event_type, event_version, topic, partition_key, payload,
      created_at, retention_until
    ) VALUES (?, ?, 'work_item.updated', 1, 'workbench.work-item', ?, '{}'::jsonb,
              ?, ?)
    """
      .trimIndent(),
    outboxId,
    "evt_$outboxId",
    "wki_$outboxId",
    updatedAt,
    updatedAt.plusDays(30),
  )
  jdbc.update(
    """
    INSERT INTO domain_event_deliveries (
      outbox_id, consumer_name, partition_key, status, created_at, updated_at
    ) VALUES (?, ?, ?, ?, ?, ?)
    """
      .trimIndent(),
    outboxId,
    "consumer-$status",
    "wki_$outboxId",
    status,
    updatedAt,
    updatedAt,
  )
}
