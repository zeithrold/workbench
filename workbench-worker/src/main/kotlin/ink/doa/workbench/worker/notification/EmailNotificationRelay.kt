package ink.doa.workbench.worker.notification

import ink.doa.workbench.core.notification.EmailMessage
import ink.doa.workbench.core.notification.EmailSender
import ink.doa.workbench.data.notification.NotificationDeliveryRepository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class EmailNotificationRelay(
  private val repository: NotificationDeliveryRepository,
  private val sender: EmailSender,
) {
  private val logger = LoggerFactory.getLogger(javaClass)

  @Suppress("TooGenericExceptionCaught")
  @Scheduled(fixedDelayString = "\${workbench.notification.email-relay-delay-ms:5000}")
  fun relay() {
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    repository.claimEmails(25, now, now.plusMinutes(2)).forEach { delivery ->
      try {
        sender.send(EmailMessage(delivery.recipient, delivery.subject, delivery.body))
        repository.markSent(delivery.deliveryId, OffsetDateTime.now(ZoneOffset.UTC))
      } catch (error: Exception) {
        val attempts = delivery.attempts + 1
        repository.markFailed(
          delivery.deliveryId,
          attempts,
          OffsetDateTime.now(ZoneOffset.UTC).plusSeconds((1L shl attempts.coerceAtMost(6))),
          error.message ?: error.javaClass.simpleName,
        )
        logger.error(
          "notification_email_failed deliveryId={} attempts={}",
          delivery.deliveryId,
          attempts,
          error,
        )
      }
    }
  }
}
