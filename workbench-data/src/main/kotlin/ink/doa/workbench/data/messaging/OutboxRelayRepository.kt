package ink.doa.workbench.data.messaging

import java.time.OffsetDateTime
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

data class OutboxMessage(
  val id: java.util.UUID,
  val topic: String,
  val partitionKey: String,
  val payload: String,
  val attempts: Int,
)

@Repository
class OutboxRelayRepository(private val jdbc: JdbcTemplate) {
  fun claim(limit: Int, now: OffsetDateTime, lockedUntil: OffsetDateTime): List<OutboxMessage> {
    val candidates =
      jdbc.query(
        """
        WITH candidates AS (
          SELECT id
          FROM domain_outbox
          WHERE status IN ('PENDING', 'RETRY')
            AND next_attempt_at <= ?
            AND (locked_until IS NULL OR locked_until < ?)
          ORDER BY created_at
          LIMIT ?
          FOR UPDATE SKIP LOCKED
        )
        UPDATE domain_outbox o
        SET locked_until = ?, updated_at = ?
        FROM candidates c
        WHERE o.id = c.id
        RETURNING o.id, o.topic, o.partition_key, o.payload::text, o.attempts
        """
          .trimIndent(),
        { rs, _ ->
          OutboxMessage(
            id = rs.getObject("id", java.util.UUID::class.java),
            topic = rs.getString("topic"),
            partitionKey = rs.getString("partition_key"),
            payload = rs.getString("payload"),
            attempts = rs.getInt("attempts"),
          )
        },
        now,
        now,
        limit,
        lockedUntil,
        now,
      )
    return candidates
  }

  fun markPublished(id: java.util.UUID, now: OffsetDateTime) {
    jdbc.update(
      """
      UPDATE domain_outbox
      SET status = 'PUBLISHED', published_at = ?, locked_until = NULL, updated_at = ?
      WHERE id = ?
      """
        .trimIndent(),
      now,
      now,
      id,
    )
  }

  fun markFailed(id: java.util.UUID, attempts: Int, nextAttemptAt: OffsetDateTime, error: String) {
    val status = if (attempts >= MAX_ATTEMPTS) "DEAD" else "RETRY"
    jdbc.update(
      """
      UPDATE domain_outbox
      SET status = ?, attempts = ?, next_attempt_at = ?, last_error = ?, locked_until = NULL, updated_at = ?
      WHERE id = ?
      """
        .trimIndent(),
      status,
      attempts,
      nextAttemptAt,
      error.take(2000),
      OffsetDateTime.now(),
      id,
    )
  }

  companion object {
    const val MAX_ATTEMPTS = 8
  }
}
