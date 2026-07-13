package ink.doa.workbench.identity.auth

import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import ink.doa.workbench.tenant.tenantconfig.TenantConfigService
import java.time.Duration
import org.springframework.stereotype.Service

@Service
class MagicLinkAuthService(
  private val repositories: MagicLinkAuthRepositories,
  private val tokenIssuer: MagicLinkTokenIssuer,
  private val tokenVerifier: MagicLinkTokenVerifier,
  private val delivery: MagicLinkDeliveryPort,
  private val tenantConfig: TenantConfigService,
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

    val issuedToken =
      tokenIssuer.issue(
        loginMethodId = method.id,
        tenantId = tenant.id,
        normalizedSubject = normalizedEmail,
        ttl = tokenTtl,
      )
    delivery.send(
      SendMagicLinkCommand(
        to = normalizedEmail,
        token = issuedToken.secret,
        mailConfig = mailConfig,
      )
    )
  }

  suspend fun resolveToken(token: String): MagicLinkIdentity {
    val record = tokenVerifier.verifyAndConsume(token)
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
}

data class MagicLinkIdentity(
  val user: ink.doa.workbench.identity.model.UserRecord,
  val loginAccount: ink.doa.workbench.identity.model.LoginAccountRecord,
  val tenantId: java.util.UUID,
)
