package ink.doa.workbench.data.messaging

import ink.doa.workbench.core.messaging.OutboxMessageQuery
import ink.doa.workbench.core.messaging.OutboxMessageRecord
import ink.doa.workbench.core.port.messaging.OutboxAdminStore
import ink.doa.workbench.data.persistence.postgres.toPreparedStatementSetter
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class ExposedOutboxAdminStore(private val jdbc: JdbcTemplate) : OutboxAdminStore {
  override fun list(query: OutboxMessageQuery): List<OutboxMessageRecord> {
    val conditions = mutableListOf<String>()
    val params = mutableListOf<Any>()
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
    return jdbc.query(
      """
      SELECT id, event_id, event_type, topic, partition_key, tenant_id, created_at,
             retention_until
      FROM domain_outbox
      $where
      ORDER BY created_at DESC
      LIMIT ? OFFSET ?
      """
        .trimIndent(),
      params.toPreparedStatementSetter(),
      { rs, _ -> rs.toRecord() },
    )
  }

  override fun findById(id: UUID): OutboxMessageRecord? =
    jdbc
      .query(
        """
        SELECT id, event_id, event_type, topic, partition_key, tenant_id, created_at,
               retention_until
        FROM domain_outbox
        WHERE id = ?
        """
          .trimIndent(),
        { rs, _ -> rs.toRecord() },
        id,
      )
      .singleOrNull()

  private fun java.sql.ResultSet.toRecord(): OutboxMessageRecord =
    OutboxMessageRecord(
      id = getObject("id", UUID::class.java),
      eventId = getString("event_id"),
      eventType = getString("event_type"),
      topic = getString("topic"),
      partitionKey = getString("partition_key"),
      tenantId = getString("tenant_id"),
      createdAt = getObject("created_at", OffsetDateTime::class.java),
      retentionUntil = getObject("retention_until", OffsetDateTime::class.java),
    )
}
