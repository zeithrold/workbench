package one.ztd.workbench.application.messaging

import java.time.OffsetDateTime
import java.util.UUID

enum class MessagingBackend {
  REDIS_STREAMS
}

data class ClaimedOutboxPublication(
  val outboxId: UUID,
  val topic: String,
  val partitionKey: String,
  val attempts: Int,
)

data class OutboxPublicationFailure(
  val outboxId: UUID,
  val backend: MessagingBackend,
  val epoch: String,
  val attempts: Int,
  val nextAttemptAt: OffsetDateTime,
  val error: String,
  val maxAttempts: Int,
)

interface OutboxPublicationStore {
  fun reconcile(backend: MessagingBackend, epoch: String, now: OffsetDateTime)

  fun claim(
    backend: MessagingBackend,
    epoch: String,
    limit: Int,
    now: OffsetDateTime,
    lockedUntil: OffsetDateTime,
  ): List<ClaimedOutboxPublication>

  fun markPublished(outboxId: UUID, backend: MessagingBackend, epoch: String, now: OffsetDateTime)

  fun markFailed(failure: OutboxPublicationFailure)
}
