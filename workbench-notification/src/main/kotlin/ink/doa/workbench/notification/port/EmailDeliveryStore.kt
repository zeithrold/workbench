package ink.doa.workbench.notification.port

import java.time.OffsetDateTime
import java.util.UUID

data class PendingEmailDelivery(
  val deliveryId: UUID,
  val notificationId: UUID,
  val recipient: String,
  val subject: String,
  val body: String,
  val attempts: Int,
)

interface EmailDeliveryStore {
  fun claimEmails(
    limit: Int,
    now: OffsetDateTime,
    lockedUntil: OffsetDateTime,
  ): List<PendingEmailDelivery>

  fun markSent(id: UUID, now: OffsetDateTime)

  fun markFailed(id: UUID, attempts: Int, nextAttemptAt: OffsetDateTime, error: String)
}
