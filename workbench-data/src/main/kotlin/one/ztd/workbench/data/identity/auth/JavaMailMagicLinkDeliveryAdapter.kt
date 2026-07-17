package one.ztd.workbench.data.identity.auth

import one.ztd.workbench.identity.auth.MagicLinkDeliveryPort
import one.ztd.workbench.identity.auth.SendMagicLinkCommand
import one.ztd.workbench.tenant.tenantconfig.model.MailSmtpTenantConfig
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component

@Component
class JavaMailMagicLinkDeliveryAdapter(private val mailSenderFactory: MagicLinkMailSenderFactory) :
  MagicLinkDeliveryPort {
  override suspend fun send(command: SendMagicLinkCommand) {
    val mailSender = mailSenderFactory.create(command.mailConfig)
    val message = mailSender.createMimeMessage()
    MimeMessageHelper(message, true).apply {
      setFrom(command.mailConfig.fromAddress ?: "noreply@workbench.local")
      setTo(command.to)
      setSubject("Workbench sign-in link")
      setText(
        "Use this link to sign in: /api/auth/magic-link/verify?token=${command.token}",
        false,
      )
    }
    mailSender.send(message)
  }
}

@Component
open class MagicLinkMailSenderFactory {
  open fun create(config: MailSmtpTenantConfig): JavaMailSender {
    val sender = JavaMailSenderImpl()
    sender.host = config.host ?: "localhost"
    sender.port = config.port
    sender.username = config.username
    sender.setPassword(config.passwordSecretRef)
    val props = sender.javaMailProperties
    props["mail.transport.protocol"] = "smtp"
    props["mail.smtp.auth"] = (config.username != null).toString()
    props["mail.smtp.starttls.enable"] = "true"
    return sender
  }
}
