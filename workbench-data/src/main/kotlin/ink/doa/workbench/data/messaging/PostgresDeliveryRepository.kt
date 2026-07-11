package ink.doa.workbench.data.messaging

import ink.doa.workbench.core.messaging.OutboxDeliveryQuery
import ink.doa.workbench.core.messaging.OutboxDeliveryRecord
import ink.doa.workbench.core.port.messaging.ClaimedEventDelivery
import ink.doa.workbench.core.port.messaging.DomainEventExecutionStore
import ink.doa.workbench.core.port.messaging.EventDeliveryFailure
import ink.doa.workbench.core.port.messaging.EventSubscription
import ink.doa.workbench.core.port.messaging.OutboxDeliveryAdminStore
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class PostgresDeliveryRepository(private val jdbc: JdbcTemplate) :
  OutboxDeliveryAdminStore, DomainEventExecutionStore {
  @Transactional
  override fun materialize(subscriptions: Collection<EventSubscription>) {
    subscriptions.forEach { subscription ->
      subscription.eventTypes.forEach { eventType ->
        jdbc.update(
          """
          INSERT INTO domain_event_deliveries
            (outbox_id, consumer_name, partition_key, status, next_attempt_at, created_at, updated_at)
          SELECT id, ?, partition_key, 'PENDING', now(), now(), now()
          FROM domain_outbox
          WHERE topic = ? AND event_type = ?
          ON CONFLICT (outbox_id, consumer_name) DO NOTHING
          """
            .trimIndent(),
          subscription.consumerName,
          subscription.topic,
          eventType,
        )
      }
    }
  }

  override fun claimReady(
    limit: Int,
    now: OffsetDateTime,
    lockedUntil: OffsetDateTime,
  ): List<ClaimedEventDelivery> = claimWhere(null, null, limit, now, lockedUntil)

  override fun claimByOutbox(
    outboxId: UUID,
    consumerName: String?,
    now: OffsetDateTime,
    lockedUntil: OffsetDateTime,
  ): List<ClaimedEventDelivery> =
    claimWhere(outboxId, consumerName, Int.MAX_VALUE, now, lockedUntil)

  private fun claimWhere(
    outboxId: UUID?,
    consumerName: String?,
    limit: Int,
    now: OffsetDateTime,
    lockedUntil: OffsetDateTime,
  ): List<ClaimedEventDelivery> =
    jdbc.query(
      """
      WITH candidates AS (
        SELECT d.outbox_id, d.consumer_name
        FROM domain_event_deliveries d
        JOIN domain_outbox o ON o.id = d.outbox_id
        WHERE d.status IN ('PENDING', 'RETRY', 'PROCESSING')
          AND (CAST(? AS uuid) IS NULL OR d.outbox_id = ?)
          AND (CAST(? AS text) IS NULL OR d.consumer_name = ?)
          AND d.next_attempt_at <= ?
          AND (d.locked_until IS NULL OR d.locked_until < ?)
          AND NOT EXISTS (
            SELECT 1
            FROM domain_event_deliveries prior
            JOIN domain_outbox prior_o ON prior_o.id = prior.outbox_id
            WHERE prior.consumer_name = d.consumer_name
              AND prior.partition_key = d.partition_key
              AND prior.status NOT IN ('SUCCEEDED', 'DEAD')
              AND prior_o.created_at < o.created_at
          )
        ORDER BY o.created_at
        LIMIT ?
        FOR UPDATE OF d SKIP LOCKED
      )
      UPDATE domain_event_deliveries d
      SET status = 'PROCESSING', locked_until = ?, updated_at = ?
      FROM candidates c, domain_outbox o
      WHERE d.outbox_id = c.outbox_id
        AND d.consumer_name = c.consumer_name
        AND o.id = d.outbox_id
      RETURNING d.outbox_id, d.consumer_name, d.partition_key, o.payload::text, d.attempts
      """
        .trimIndent(),
      { rs, _ ->
        ClaimedEventDelivery(
          outboxId = rs.getObject("outbox_id", UUID::class.java),
          consumerName = rs.getString("consumer_name"),
          partitionKey = rs.getString("partition_key"),
          payload = rs.getString("payload"),
          attempts = rs.getInt("attempts"),
        )
      },
      outboxId,
      outboxId,
      consumerName,
      consumerName,
      now,
      now,
      limit,
      lockedUntil,
      now,
    )

  override fun markSucceeded(outboxId: UUID, consumerName: String, now: OffsetDateTime) {
    jdbc.update(
      """
      UPDATE domain_event_deliveries
      SET status = 'SUCCEEDED', locked_until = NULL, completed_at = ?, updated_at = ?
      WHERE outbox_id = ? AND consumer_name = ?
      """
        .trimIndent(),
      now,
      now,
      outboxId,
      consumerName,
    )
  }

  override fun markFailed(failure: EventDeliveryFailure) {
    jdbc.update(
      """
      UPDATE domain_event_deliveries
      SET status = ?, attempts = ?, next_attempt_at = ?, locked_until = NULL,
          last_error = ?, updated_at = now()
      WHERE outbox_id = ? AND consumer_name = ?
      """
        .trimIndent(),
      if (failure.attempts >= failure.maxAttempts) "DEAD" else "RETRY",
      failure.attempts,
      failure.nextAttemptAt,
      failure.error.take(2000),
      failure.outboxId,
      failure.consumerName,
    )
  }

  override fun listDeliveries(query: OutboxDeliveryQuery): List<OutboxDeliveryRecord> {
    return jdbc.query(
      """
      SELECT outbox_id, consumer_name, partition_key, status, attempts, next_attempt_at,
             locked_until, last_error, completed_at, created_at, updated_at
      FROM domain_event_deliveries
      WHERE (CAST(? AS text) IS NULL OR status = ?)
        AND (CAST(? AS text) IS NULL OR consumer_name = ?)
      ORDER BY created_at DESC LIMIT ? OFFSET ?
      """
        .trimIndent(),
      { rs, _ ->
        OutboxDeliveryRecord(
          rs.getObject("outbox_id", UUID::class.java),
          rs.getString("consumer_name"),
          rs.getString("partition_key"),
          rs.getString("status"),
          rs.getInt("attempts"),
          rs.getObject("next_attempt_at", OffsetDateTime::class.java),
          rs.getObject("locked_until", OffsetDateTime::class.java),
          rs.getString("last_error"),
          rs.getObject("completed_at", OffsetDateTime::class.java),
          rs.getObject("created_at", OffsetDateTime::class.java),
          rs.getObject("updated_at", OffsetDateTime::class.java),
        )
      },
      query.status,
      query.status,
      query.consumerName,
      query.consumerName,
      query.limit.coerceIn(1, 100),
      query.offset.coerceAtLeast(0),
    )
  }

  override fun replayDeadDelivery(outboxId: UUID, consumerName: String): Boolean =
    jdbc.update(
      """
      UPDATE domain_event_deliveries
      SET status = 'PENDING', attempts = 0, next_attempt_at = now(), locked_until = NULL,
          last_error = NULL, completed_at = NULL, updated_at = now()
      WHERE outbox_id = ? AND consumer_name = ? AND status = 'DEAD'
      """
        .trimIndent(),
      outboxId,
      consumerName,
    ) > 0
}
