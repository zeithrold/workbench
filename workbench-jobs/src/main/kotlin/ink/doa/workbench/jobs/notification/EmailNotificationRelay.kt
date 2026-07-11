package ink.doa.workbench.jobs.notification

import ink.doa.workbench.core.notification.EmailMessage
import ink.doa.workbench.core.notification.EmailSender
import ink.doa.workbench.core.port.notification.EmailDeliveryStore
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@ink.doa.workbench.jobs.JobsEnabled
@Component
class EmailNotificationRelay(
  private val repository: EmailDeliveryStore,
  private val sender: EmailSender,
) {
  private val logger = LoggerFactory.getLogger(javaClass)

  @Scheduled(fixedDelayString = "\${workbench.notification.email-relay-delay-ms:5000}")
  fun relay() {
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    repository.claimEmails(25, now, now.plusMinutes(2)).forEach { delivery ->
      try {
        sender.send(EmailMessage(delivery.recipient, delivery.subject, delivery.body))
        repository.markSent(delivery.deliveryId, OffsetDateTime.now(ZoneOffset.UTC))
      } catch (
        // Relay boundary: every sender failure must be persisted for retry or dead-lettering.
        @Suppress("TooGenericExceptionCaught") error: Exception) {
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
