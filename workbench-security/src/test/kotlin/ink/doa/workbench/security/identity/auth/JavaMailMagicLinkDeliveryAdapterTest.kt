package ink.doa.workbench.security.identity.auth

import ink.doa.workbench.core.tenantconfig.model.MailSmtpTenantConfig
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Properties
import kotlinx.coroutines.runBlocking
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessagePreparator

class JavaMailMagicLinkDeliveryAdapterTest :
  StringSpec({
    "send creates and sends a magic link message through the configured sender" {
      val sender = RecordingJavaMailSender()
      val adapter = JavaMailMagicLinkDeliveryAdapter(FixedMagicLinkMailSenderFactory(sender))
      val config =
        MailSmtpTenantConfig(
          enabled = true,
          fromAddress = "login@acme.test",
          host = "smtp.acme.test",
          port = 2525,
          username = "mailer",
          passwordSecretRef = "secret",
        )

      runBlocking {
        adapter.send(
          SendMagicLinkCommand(
            to = "ada@example.test",
            token = "magic-secret",
            mailConfig = config,
          )
        )
      }

      sender.sent shouldHaveSize 1
      val message = sender.sent.single()
      message.subject shouldBe "Workbench sign-in link"
      message.allRecipients.single().toString() shouldBe "ada@example.test"
      message.from.single().toString() shouldBe "login@acme.test"
      val rawMessage = ByteArrayOutputStream().also(message::writeTo).toString(Charsets.UTF_8)
      rawMessage shouldContain "/api/auth/magic-link/verify?token=magic-secret"
    }

    "factory maps tenant smtp config to JavaMail sender properties" {
      val sender =
        MagicLinkMailSenderFactory()
          .create(
            MailSmtpTenantConfig(
              enabled = true,
              fromAddress = null,
              host = null,
              port = 1025,
              username = "mailer",
              passwordSecretRef = "secret",
            )
          ) as org.springframework.mail.javamail.JavaMailSenderImpl

      sender.host shouldBe "localhost"
      sender.port shouldBe 1025
      sender.username shouldBe "mailer"
      sender.javaMailProperties["mail.transport.protocol"] shouldBe "smtp"
      sender.javaMailProperties["mail.smtp.auth"] shouldBe "true"
      sender.javaMailProperties["mail.smtp.starttls.enable"] shouldBe "true"
    }
  })

private class FixedMagicLinkMailSenderFactory(private val sender: JavaMailSender) :
  MagicLinkMailSenderFactory() {
  override fun create(config: MailSmtpTenantConfig): JavaMailSender = sender
}

private class RecordingJavaMailSender : JavaMailSender {
  val sent = mutableListOf<MimeMessage>()

  override fun createMimeMessage(): MimeMessage = MimeMessage(Session.getInstance(Properties()))

  override fun createMimeMessage(contentStream: InputStream): MimeMessage =
    MimeMessage(Session.getInstance(Properties()), contentStream)

  override fun send(mimeMessage: MimeMessage) {
    sent += mimeMessage
  }

  override fun send(vararg mimeMessages: MimeMessage) {
    sent += mimeMessages
  }

  override fun send(mimeMessagePreparator: MimeMessagePreparator) {
    val message = createMimeMessage()
    mimeMessagePreparator.prepare(message)
    sent += message
  }

  override fun send(vararg mimeMessagePreparators: MimeMessagePreparator) {
    mimeMessagePreparators.forEach(::send)
  }

  override fun send(simpleMessage: SimpleMailMessage) {
    error("Simple mail messages are not used by magic link delivery")
  }

  override fun send(vararg simpleMessages: SimpleMailMessage) {
    error("Simple mail messages are not used by magic link delivery")
  }
}
