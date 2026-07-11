package ink.doa.workbench.core.port.messaging

import java.time.OffsetDateTime
import java.util.UUID

data class EventSubscription(
  val consumerName: String,
  val topic: String,
  val eventTypes: Set<String>,
)

data class ClaimedEventDelivery(
  val outboxId: UUID,
  val consumerName: String,
  val partitionKey: String,
  val payload: String,
  val attempts: Int,
)

data class EventDeliveryFailure(
  val outboxId: UUID,
  val consumerName: String,
  val attempts: Int,
  val nextAttemptAt: OffsetDateTime,
  val error: String,
  val maxAttempts: Int,
)

interface DomainEventExecutionStore {
  fun materialize(subscriptions: Collection<EventSubscription>)

  fun claimReady(
    limit: Int,
    now: OffsetDateTime,
    lockedUntil: OffsetDateTime,
  ): List<ClaimedEventDelivery>

  fun claimByOutbox(
    outboxId: UUID,
    consumerName: String?,
    now: OffsetDateTime,
    lockedUntil: OffsetDateTime,
  ): List<ClaimedEventDelivery>

  fun markSucceeded(outboxId: UUID, consumerName: String, now: OffsetDateTime)

  fun markFailed(failure: EventDeliveryFailure)
}
