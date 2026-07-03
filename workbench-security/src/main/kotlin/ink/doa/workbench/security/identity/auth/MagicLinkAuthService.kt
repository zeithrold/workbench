@file:Suppress("ThrowsCount")

package ink.doa.workbench.security.identity.auth

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.identity.LoginAccountStore
import ink.doa.workbench.core.identity.LoginMethodRepository
import ink.doa.workbench.core.identity.TenantLoginMethodSettingRepository
import ink.doa.workbench.core.identity.TenantRepository
import ink.doa.workbench.core.identity.UserLoginAccountRepository
import ink.doa.workbench.core.identity.auth.CredentialHasher
import ink.doa.workbench.core.identity.auth.CredentialSecretGenerator
import ink.doa.workbench.core.identity.auth.MagicLinkTokenRepository
import ink.doa.workbench.core.identity.model.LoginMethodKind
import ink.doa.workbench.core.tenantconfig.model.MailSmtpTenantConfig
import ink.doa.workbench.core.tenantconfig.model.TenantConfigSpecs
import ink.doa.workbench.tenant.tenantconfig.TenantConfigService
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service

@Service
class MagicLinkAuthService(
  private val loginMethods: LoginMethodRepository,
  private val tenantLoginSettings: TenantLoginMethodSettingRepository,
  private val loginAccounts: LoginAccountStore,
  private val userLoginAccounts: UserLoginAccountRepository,
  private val tenants: TenantRepository,
  private val magicLinkTokens: MagicLinkTokenRepository,
  private val credentialHasher: CredentialHasher,
  private val secretGenerator: CredentialSecretGenerator,
  private val tenantConfig: TenantConfigService,
  private val clock: Clock,
) {
  private val tokenTtl = Duration.ofMinutes(15)

  suspend fun requestMagicLink(email: String, tenantId: String, loginMethodId: String) {
    val normalizedEmail = normalizeSubject(email)
    val tenant =
      tenants.findByApiId(tenantId)
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.RESOURCE_TENANT_NOT_FOUND,
          "Unknown tenant: $tenantId",
        )
    val method =
      loginMethods.findLoginMethodByApiId(loginMethodId)
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.RESOURCE_LOGIN_METHOD_NOT_FOUND,
          "Unknown login method: $loginMethodId",
        )
    if (method.kind != LoginMethodKind.EMAIL_MAGIC_LINK) {
      throw InvalidRequestException(
        WorkbenchErrorCode.IDENTITY_LOGIN_METHOD_NOT_MAGIC_LINK,
        "Login method $loginMethodId is not email_magic_link.",
      )
    }
    val setting = tenantLoginSettings.findTenantSetting(tenant.id, method.id)
    if (setting?.isEnabled != true) {
      throw InvalidRequestException(WorkbenchErrorCode.IDENTITY_MAGIC_LINK_DISABLED)
    }

    val mailConfig = tenantConfig.get(tenant.id, TenantConfigSpecs.MailSmtp)
    if (!mailConfig.enabled) {
      throw InvalidRequestException(WorkbenchErrorCode.IDENTITY_MAGIC_LINK_MAIL_NOT_CONFIGURED)
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
        ?: throw InvalidRequestException(WorkbenchErrorCode.IDENTITY_MAGIC_LINK_INVALID)
    magicLinkTokens.consume(record.id, now)
    val method =
      loginMethods.findLoginMethodById(record.loginMethodId)
        ?: throw InvalidRequestException(WorkbenchErrorCode.IDENTITY_MAGIC_LINK_NOT_CONFIGURED)
    val account =
      loginAccounts.findLoginAccountByMethodAndSubject(method.code, record.normalizedSubject)
        ?: throw InvalidRequestException(WorkbenchErrorCode.IDENTITY_MAGIC_LINK_ACCOUNT_NOT_FOUND)
    val user =
      userLoginAccounts.findLinkedUser(account.id)
        ?: throw InvalidRequestException(WorkbenchErrorCode.IDENTITY_MAGIC_LINK_USER_NOT_FOUND)
    return MagicLinkIdentity(
      user = user,
      loginAccount = account,
      tenantId = record.tenantId,
    )
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
