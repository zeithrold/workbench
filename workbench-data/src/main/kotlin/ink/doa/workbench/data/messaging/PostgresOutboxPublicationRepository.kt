package ink.doa.workbench.data.messaging

import ink.doa.workbench.application.messaging.ClaimedOutboxPublication
import ink.doa.workbench.application.messaging.MessagingBackend
import ink.doa.workbench.application.messaging.OutboxPublicationFailure
import ink.doa.workbench.application.messaging.OutboxPublicationStore
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class PostgresOutboxPublicationRepository(private val jdbc: JdbcTemplate) : OutboxPublicationStore {
  @Transactional
  override fun reconcile(backend: MessagingBackend, epoch: String, now: OffsetDateTime) {
    jdbc.update(
      """
      INSERT INTO outbox_transport_publications
        (outbox_id, backend, epoch, status, next_attempt_at, created_at, updated_at)
      SELECT DISTINCT d.outbox_id, ?, ?, 'PENDING', ?, ?, ?
      FROM domain_event_deliveries d
      WHERE d.status NOT IN ('SUCCEEDED', 'DEAD')
      ON CONFLICT (outbox_id, backend, epoch) DO NOTHING
      """
        .trimIndent(),
      backend.name,
      epoch,
      now,
      now,
      now,
    )
  }

  @Transactional
  override fun claim(
    backend: MessagingBackend,
    epoch: String,
    limit: Int,
    now: OffsetDateTime,
    lockedUntil: OffsetDateTime,
  ): List<ClaimedOutboxPublication> =
    jdbc.query(
      """
      WITH candidates AS (
        SELECT p.outbox_id
        FROM outbox_transport_publications p
        WHERE p.backend = ? AND p.epoch = ?
          AND p.status IN ('PENDING', 'RETRY', 'PROCESSING')
          AND p.next_attempt_at <= ?
          AND (p.locked_until IS NULL OR p.locked_until < ?)
        ORDER BY p.created_at
        LIMIT ? FOR UPDATE SKIP LOCKED
      )
      UPDATE outbox_transport_publications p
      SET status = 'PROCESSING', locked_until = ?, updated_at = ?
      FROM candidates c, domain_outbox o
      WHERE p.outbox_id = c.outbox_id AND o.id = p.outbox_id
      RETURNING p.outbox_id, o.topic, o.partition_key, p.attempts
      """
        .trimIndent(),
      { rs, _ ->
        ClaimedOutboxPublication(
          rs.getObject("outbox_id", UUID::class.java),
          rs.getString("topic"),
          rs.getString("partition_key"),
          rs.getInt("attempts"),
        )
      },
      backend.name,
      epoch,
      now,
      now,
      limit,
      lockedUntil,
      now,
    )

  override fun markPublished(
    outboxId: UUID,
    backend: MessagingBackend,
    epoch: String,
    now: OffsetDateTime,
  ) {
    jdbc.update(
      """
      UPDATE outbox_transport_publications
      SET status = 'PUBLISHED', published_at = ?, locked_until = NULL, updated_at = ?
      WHERE outbox_id = ? AND backend = ? AND epoch = ?
      """
        .trimIndent(),
      now,
      now,
      outboxId,
      backend.name,
      epoch,
    )
  }

  override fun markFailed(failure: OutboxPublicationFailure) {
    jdbc.update(
      """
      UPDATE outbox_transport_publications
      SET status = ?, attempts = ?, next_attempt_at = ?, locked_until = NULL,
          last_error = ?, updated_at = now()
      WHERE outbox_id = ? AND backend = ? AND epoch = ?
      """
        .trimIndent(),
      if (failure.attempts >= failure.maxAttempts) "DEAD" else "RETRY",
      failure.attempts,
      failure.nextAttemptAt,
      failure.error.take(2000),
      failure.outboxId,
      failure.backend.name,
      failure.epoch,
    )
  }
}
