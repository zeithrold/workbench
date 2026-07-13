package ink.doa.workbench.core.messaging

import java.time.OffsetDateTime
import java.util.UUID

data class OutboxMessageRecord(
  val id: UUID,
  val eventId: String,
  val eventType: String,
  val topic: String,
  val partitionKey: String,
  val tenantId: String?,
  val createdAt: OffsetDateTime,
  val retentionUntil: OffsetDateTime,
)

data class OutboxMessageQuery(
  val tenantId: String? = null,
  val eventType: String? = null,
  val limit: Int = 50,
  val offset: Long = 0,
)
