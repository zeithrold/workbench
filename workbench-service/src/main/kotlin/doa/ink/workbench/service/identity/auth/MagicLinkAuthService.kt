@file:Suppress("ThrowsCount")

package doa.ink.workbench.service.identity.auth

import doa.ink.workbench.core.common.errors.InvalidRequestException
import doa.ink.workbench.core.identity.LoginAccountRepository
import doa.ink.workbench.core.identity.TenantRepository
import doa.ink.workbench.core.identity.auth.CredentialHasher
import doa.ink.workbench.core.identity.auth.CredentialSecretGenerator
import doa.ink.workbench.core.identity.auth.MagicLinkTokenRepository
import doa.ink.workbench.core.identity.model.LoginMethodKind
import doa.ink.workbench.core.tenantconfig.model.MailSmtpTenantConfig
import doa.ink.workbench.core.tenantconfig.model.TenantConfigSpecs
import doa.ink.workbench.service.tenantconfig.TenantConfigService
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service

@Service
class MagicLinkAuthService(
  private val loginAccounts: LoginAccountRepository,
  private val tenants: TenantRepository,
  private val magicLinkTokens: MagicLinkTokenRepository,
  private val credentialHasher: CredentialHasher,
  private val secretGenerator: CredentialSecretGenerator,
  private val tenantConfig: TenantConfigService,
  private val clock: Clock,
) {
  private val tokenTtl = Duration.ofMinutes(15)

  suspend fun requestMagicLink(email: String, tenantApiId: String, loginMethodCode: String) {
    val normalizedEmail = normalizeSubject(email)
    val tenant =
      tenants.findByApiId(tenantApiId)
        ?: throw InvalidRequestException("Unknown tenant: $tenantApiId")
    val method =
      loginAccounts.findLoginMethodByCode(loginMethodCode)
        ?: throw InvalidRequestException("Unknown login method: $loginMethodCode")
    if (method.kind != LoginMethodKind.EMAIL_MAGIC_LINK) {
      throw InvalidRequestException("Login method $loginMethodCode is not email_magic_link.")
    }
    val setting = loginAccounts.findTenantSetting(tenant.id, method.id)
    if (setting?.isEnabled != true) {
      throw InvalidRequestException("Magic link login is disabled for this tenant.")
    }

    val mailConfig = tenantConfig.get(tenant.id, TenantConfigSpecs.MailSmtp)
    if (!mailConfig.enabled) {
      throw InvalidRequestException("Outbound mail is not configured for this tenant.")
    }

    val secret = secretGenerator.generate()
    val now = OffsetDateTime.now(clock)
    magicLinkTokens.create(
      tokenHash = credentialHasher.hash(secret),
      loginMethodId = method.id,
      tenantId = tenant.id,
      normalizedSubject = normalizedEmail,
      expiresAt = now.plus(tokenTtl),
    )

    val mailSender = buildMailSender(mailConfig)
    val message = mailSender.createMimeMessage()
    MimeMessageHelper(message, true).apply {
      setFrom(mailConfig.fromAddress ?: "noreply@workbench.local")
      setTo(normalizedEmail)
      setSubject("Workbench sign-in link")
      setText("Use this link to sign in: /api/auth/magic-link/verify?token=$secret", false)
    }
    mailSender.send(message)
  }

  suspend fun resolveToken(token: String): MagicLinkIdentity {
    val now = OffsetDateTime.now(clock)
    val record =
      magicLinkTokens.findActiveByHash(credentialHasher.hash(token), now)
        ?: throw InvalidRequestException("Magic link is invalid or expired.")
    magicLinkTokens.consume(record.id, now)
    val method =
      loginAccounts.findLoginMethodByCode("email_magic_link")
        ?: throw InvalidRequestException("Magic link login is not configured.")
    val account =
      loginAccounts.findLoginAccountByMethodAndSubject(method.code, record.normalizedSubject)
        ?: throw InvalidRequestException("No account is linked to this magic link.")
    val user =
      loginAccounts.findLinkedUser(account.id)
        ?: throw InvalidRequestException("No user is linked to this magic link.")
    return MagicLinkIdentity(user = user, loginAccount = account)
  }

  private fun buildMailSender(config: MailSmtpTenantConfig): JavaMailSenderImpl {
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

data class MagicLinkIdentity(
  val user: doa.ink.workbench.core.identity.model.UserRecord,
  val loginAccount: doa.ink.workbench.core.identity.model.LoginAccountRecord,
)
