package ink.doa.workbench.application.messaging

import java.time.OffsetDateTime
import java.util.UUID

data class OutboxDeliveryQuery(
  val status: String? = null,
  val consumerName: String? = null,
  val limit: Int = 50,
  val offset: Long = 0,
)

data class OutboxDeliveryRecord(
  val outboxId: UUID,
  val consumerName: String,
  val partitionKey: String,
  val status: String,
  val attempts: Int,
  val nextAttemptAt: OffsetDateTime,
  val lockedUntil: OffsetDateTime?,
  val lastError: String?,
  val completedAt: OffsetDateTime?,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)
