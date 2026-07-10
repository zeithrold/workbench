package ink.doa.workbench.data.notification

import ink.doa.workbench.core.notification.EmailMessage
import ink.doa.workbench.core.notification.EmailSender
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** Safe default sender for local development; production can replace this adapter. */
@Component
class LoggingEmailSender : EmailSender {
  private val logger = LoggerFactory.getLogger(javaClass)

  override fun send(message: EmailMessage) {
    logger.info(
      "notification_email_sent recipient={} subject={}",
      message.recipient,
      message.subject,
    )
  }
}
