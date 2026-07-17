package one.ztd.workbench.data.notification

import java.time.OffsetDateTime
import one.ztd.workbench.notification.port.EmailDeliveryStore
import one.ztd.workbench.notification.port.PendingEmailDelivery
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class NotificationDeliveryRepository(private val jdbc: JdbcTemplate) : EmailDeliveryStore {
  override fun claimEmails(
    limit: Int,
    now: OffsetDateTime,
    lockedUntil: OffsetDateTime,
  ): List<PendingEmailDelivery> =
    jdbc.query(
      """
      WITH candidates AS (
        SELECT d.id, d.notification_id
        FROM notification_deliveries d
        WHERE d.channel = 'EMAIL'
          AND d.status IN ('PENDING', 'RETRY')
          AND d.next_attempt_at <= ?
        ORDER BY d.next_attempt_at
        LIMIT ?
        FOR UPDATE SKIP LOCKED
      )
      UPDATE notification_deliveries d
      SET status = 'RETRY', next_attempt_at = ?
      FROM candidates c
      JOIN notifications n ON n.id = c.notification_id
      JOIN users u ON u.id = n.recipient_user_id
      WHERE d.id = c.id
      RETURNING d.id, d.notification_id, u.primary_email, n.title, n.body, d.attempts
      """
        .trimIndent(),
      { rs, _ ->
        PendingEmailDelivery(
          deliveryId = rs.getObject("id", java.util.UUID::class.java),
          notificationId = rs.getObject("notification_id", java.util.UUID::class.java),
          recipient = rs.getString("primary_email"),
          subject = rs.getString("title"),
          body = rs.getString("body"),
          attempts = rs.getInt("attempts"),
        )
      },
      now,
      limit,
      lockedUntil,
    )

  override fun markSent(id: java.util.UUID, now: OffsetDateTime) {
    jdbc.update(
      "UPDATE notification_deliveries SET status = 'SENT', sent_at = ?, last_error = NULL WHERE id = ?",
      now,
      id,
    )
  }

  override fun markFailed(
    id: java.util.UUID,
    attempts: Int,
    nextAttemptAt: OffsetDateTime,
    error: String,
  ) {
    val status = if (attempts >= 8) "DEAD" else "RETRY"
    jdbc.update(
      "UPDATE notification_deliveries SET status = ?, attempts = ?, next_attempt_at = ?, last_error = ? WHERE id = ?",
      status,
      attempts,
      nextAttemptAt,
      error.take(2000),
      id,
    )
  }
}
