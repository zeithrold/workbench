package ink.doa.workbench.security.identity.auth

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.tenantconfig.model.MailSmtpTenantConfig
import ink.doa.workbench.tenant.tenantconfig.TenantConfigService
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service

@Service
class MagicLinkAuthService(
  private val repositories: MagicLinkAuthRepositories,
  private val crypto: CredentialCryptoSupport,
  private val tenantConfig: TenantConfigService,
  private val clock: Clock,
) {
  private val tokenTtl = Duration.ofMinutes(15)

  suspend fun requestMagicLink(email: String, tenantId: String, loginMethodId: String) {
    val normalizedEmail = normalizeSubject(email)
    val tenant = requireTenantByApiId(repositories.tenants, tenantId)
    val method = requireLoginMethodByApiId(repositories.loginMethods, loginMethodId)
    requireMagicLinkMethod(method, loginMethodId)
    requireEnabledTenantSetting(
      repositories.tenantLoginSettings.findTenantSetting(tenant.id, method.id)
    )
    val mailConfig = requireMagicLinkMailConfig(tenantConfig, tenant.id)

    val secret = crypto.secretGenerator.generate()
    val now = OffsetDateTime.now(clock)
    repositories.magicLinkTokens.create(
      tokenHash = crypto.credentialHasher.hash(secret),
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
      repositories.magicLinkTokens.findActiveByHash(crypto.credentialHasher.hash(token), now)
        ?: throw InvalidRequestException(WorkbenchErrorCode.IDENTITY_MAGIC_LINK_INVALID)
    repositories.magicLinkTokens.consume(record.id, now)
    return resolveMagicLinkIdentity(record.loginMethodId, record.normalizedSubject, record.tenantId)
  }

  private suspend fun resolveMagicLinkIdentity(
    loginMethodId: java.util.UUID,
    normalizedSubject: String,
    tenantId: java.util.UUID,
  ): MagicLinkIdentity {
    val method =
      repositories.loginMethods.findLoginMethodById(loginMethodId)
        ?: throw InvalidRequestException(WorkbenchErrorCode.IDENTITY_MAGIC_LINK_NOT_CONFIGURED)
    val account =
      repositories.loginAccounts.findLoginAccountByMethodAndSubject(method.code, normalizedSubject)
        ?: throw InvalidRequestException(WorkbenchErrorCode.IDENTITY_MAGIC_LINK_ACCOUNT_NOT_FOUND)
    val user =
      repositories.userLoginAccounts.findLinkedUser(account.id)
        ?: throw InvalidRequestException(WorkbenchErrorCode.IDENTITY_MAGIC_LINK_USER_NOT_FOUND)
    return MagicLinkIdentity(user = user, loginAccount = account, tenantId = tenantId)
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
  val user: ink.doa.workbench.core.identity.model.UserRecord,
  val loginAccount: ink.doa.workbench.core.identity.model.LoginAccountRecord,
  val tenantId: java.util.UUID,
)
