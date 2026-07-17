package one.ztd.workbench.data.messaging

import java.time.OffsetDateTime
import one.ztd.workbench.application.messaging.OutboxRetentionStore
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class PostgresOutboxRetentionRepository(private val jdbc: JdbcTemplate) : OutboxRetentionStore {
  @Transactional
  override fun deleteExpiredTerminal(limit: Int, now: OffsetDateTime): Int =
    jdbc.update(
      """
      WITH expired AS (
        SELECT o.id
        FROM domain_outbox o
        WHERE o.retention_until <= ?
          AND NOT EXISTS (
            SELECT 1
            FROM domain_event_deliveries d
            WHERE d.outbox_id = o.id
              AND d.status NOT IN ('SUCCEEDED', 'DEAD')
          )
        ORDER BY o.retention_until
        LIMIT ?
        FOR UPDATE OF o SKIP LOCKED
      )
      DELETE FROM domain_outbox o
      USING expired e
      WHERE o.id = e.id
      """
        .trimIndent(),
      now,
      limit,
    )
}
