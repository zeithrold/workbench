package ink.doa.workbench.data.messaging

import ink.doa.workbench.core.messaging.OutboxMessageQuery
import ink.doa.workbench.core.messaging.OutboxMessageRecord
import ink.doa.workbench.core.port.messaging.OutboxAdminStore
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class ExposedOutboxAdminStore(private val jdbc: JdbcTemplate) : OutboxAdminStore {
  override fun list(query: OutboxMessageQuery): List<OutboxMessageRecord> {
    val conditions = mutableListOf<String>()
    val params = mutableListOf<Any>()
    query.status?.let {
      conditions += "status = ?"
      params += it
    }
    query.tenantId?.let {
      conditions += "tenant_id = ?"
      params += it
    }
    query.eventType?.let {
      conditions += "event_type = ?"
      params += it
    }
    val where = if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")}"
    params += query.limit.coerceIn(1, 100)
    params += query.offset.coerceAtLeast(0)
    @Suppress("SpreadOperator")
    return jdbc.query(
      """
      SELECT id, event_id, event_type, topic, partition_key, tenant_id, status,
             attempts, last_error, created_at, updated_at, next_attempt_at, published_at
      FROM domain_outbox
      $where
      ORDER BY created_at DESC
      LIMIT ? OFFSET ?
      """
        .trimIndent(),
      { rs, _ -> rs.toRecord() },
      *params.toTypedArray(),
    )
  }

  override fun findById(id: UUID): OutboxMessageRecord? =
    jdbc
      .query(
        """
        SELECT id, event_id, event_type, topic, partition_key, tenant_id, status,
               attempts, last_error, created_at, updated_at, next_attempt_at, published_at
        FROM domain_outbox
        WHERE id = ?
        """
          .trimIndent(),
        { rs, _ -> rs.toRecord() },
        id,
      )
      .singleOrNull()

  override fun countByStatus(status: String): Long =
    jdbc.queryForObject(
      "SELECT count(*) FROM domain_outbox WHERE status = ?",
      Long::class.java,
      status,
    ) ?: 0L

  override fun replayDead(id: UUID): Boolean {
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    return jdbc.update(
      """
      UPDATE domain_outbox
      SET status = 'RETRY', attempts = 0, next_attempt_at = ?, locked_until = NULL,
          last_error = NULL, updated_at = ?
      WHERE id = ? AND status = 'DEAD'
      """
        .trimIndent(),
      now,
      now,
      id,
    ) > 0
  }

  private fun java.sql.ResultSet.toRecord(): OutboxMessageRecord =
    OutboxMessageRecord(
      id = getObject("id", UUID::class.java),
      eventId = getString("event_id"),
      eventType = getString("event_type"),
      topic = getString("topic"),
      partitionKey = getString("partition_key"),
      tenantId = getString("tenant_id"),
      status = getString("status"),
      attempts = getInt("attempts"),
      lastError = getString("last_error"),
      createdAt = getObject("created_at", OffsetDateTime::class.java),
      updatedAt = getObject("updated_at", OffsetDateTime::class.java),
      nextAttemptAt = getObject("next_attempt_at", OffsetDateTime::class.java),
      publishedAt = getObject("published_at", OffsetDateTime::class.java),
    )
}
