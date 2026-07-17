package one.ztd.workbench.security.identity.auth

import one.ztd.workbench.identity.LoginAccountStore
import one.ztd.workbench.identity.LoginMethodRepository
import one.ztd.workbench.identity.TenantLoginMethodSettingRepository
import one.ztd.workbench.identity.UserLoginAccountRepository
import one.ztd.workbench.identity.auth.LoginAuthenticator
import one.ztd.workbench.identity.auth.PasswordVerifier
import one.ztd.workbench.identity.auth.authInvalidCredentials
import one.ztd.workbench.identity.auth.normalizeSubject
import one.ztd.workbench.identity.auth.requireEnabledLdapSetting
import one.ztd.workbench.identity.auth.requireLdapLoginMethodId
import one.ztd.workbench.identity.auth.requireLdapMethod
import one.ztd.workbench.identity.auth.requireLdapPassword
import one.ztd.workbench.identity.auth.requireLdapSubject
import one.ztd.workbench.identity.auth.requireLdapTenantId
import one.ztd.workbench.identity.auth.requireLoginPassword
import one.ztd.workbench.identity.auth.requireLoginSubject
import one.ztd.workbench.identity.auth.requireLoginToken
import one.ztd.workbench.identity.model.AuthenticatedIdentity
import one.ztd.workbench.identity.model.LoginAccountParameterKey
import one.ztd.workbench.identity.model.LoginCommand
import one.ztd.workbench.identity.model.LoginMethodKind
import one.ztd.workbench.tenant.TenantRepository
import org.springframework.stereotype.Component

private const val PASSWORD_METHOD_CODE = "password"

@Component
class PasswordLoginAuthenticator(
  private val loginMethods: LoginMethodRepository,
  private val loginAccounts: LoginAccountStore,
  private val userLoginAccounts: UserLoginAccountRepository,
  private val passwordVerifier: PasswordVerifier,
) : LoginAuthenticator {
  override val kind: LoginMethodKind = LoginMethodKind.PASSWORD

  override suspend fun authenticate(command: LoginCommand): AuthenticatedIdentity {
    val normalizedSubject = normalizeSubject(requireLoginSubject(command))
    val methodCode =
      command.loginMethodId?.let { apiId ->
        loginMethods.findLoginMethodByApiId(apiId)?.code
      } ?: PASSWORD_METHOD_CODE
    val account =
      loginAccounts.findLoginAccountByMethodAndSubject(methodCode, normalizedSubject)
        ?: authInvalidCredentials()

    val passwordHash =
      loginAccounts.findParameter(account.id, LoginAccountParameterKey.PasswordHash)?.parameterValue
        ?: authInvalidCredentials()

    if (!passwordVerifier.verify(requireLoginPassword(command), passwordHash)) {
      authInvalidCredentials()
    }

    val user = userLoginAccounts.findLinkedUser(account.id) ?: authInvalidCredentials()

    return AuthenticatedIdentity(user = user, loginAccount = account)
  }
}

@Component
class ApiTokenLoginAuthenticator(
  private val loginMethods: LoginMethodRepository,
  private val loginAccounts: LoginAccountStore,
  private val userLoginAccounts: UserLoginAccountRepository,
  private val credentialHasher: one.ztd.workbench.identity.auth.CredentialHasher,
) : LoginAuthenticator {
  override val kind: LoginMethodKind = LoginMethodKind.API_TOKEN

  override suspend fun authenticate(command: LoginCommand): AuthenticatedIdentity {
    val methodCode =
      command.loginMethodId?.let { loginMethods.findLoginMethodByApiId(it)?.code } ?: "api_token"
    val tokenHash = credentialHasher.hash(requireLoginToken(command))

    val account =
      loginAccounts.findLoginAccountByParameterValue(
        loginMethodCode = methodCode,
        parameterKey = LoginAccountParameterKey.ApiTokenHash,
        parameterValue = tokenHash,
      ) ?: authInvalidCredentials()

    val user = userLoginAccounts.findLinkedUser(account.id) ?: authInvalidCredentials()

    return AuthenticatedIdentity(user = user, loginAccount = account)
  }
}

@Component
class LdapLoginAuthenticator(
  private val loginMethods: LoginMethodRepository,
  private val tenantLoginSettings: TenantLoginMethodSettingRepository,
  private val loginAccounts: LoginAccountStore,
  private val userLoginAccounts: UserLoginAccountRepository,
  private val tenants: TenantRepository,
  private val ldapClient: LdapAuthClient,
) : LoginAuthenticator {
  override val kind: LoginMethodKind = LoginMethodKind.LDAP

  override suspend fun authenticate(command: LoginCommand): AuthenticatedIdentity {
    val loginMethodId = requireLdapLoginMethodId(command)
    val tenantId = requireLdapTenantId(command)
    val tenant = tenants.findByApiId(tenantId) ?: authInvalidCredentials()
    val method =
      loginMethods.findLoginMethodByApiId(loginMethodId)?.also {
        requireLdapMethod(it, loginMethodId)
      } ?: authInvalidCredentials()
    val setting = tenantLoginSettings.findTenantSetting(tenant.id, method.id)
    requireEnabledLdapSetting(setting)
    val enabledSetting = requireNotNull(setting)

    val normalizedSubject =
      ldapClient.authenticate(
        enabledSetting,
        requireLdapSubject(command),
        requireLdapPassword(command),
      )
    val account =
      loginAccounts.findLoginAccountByMethodAndSubject(method.code, normalizedSubject)
        ?: authInvalidCredentials()
    val user = userLoginAccounts.findLinkedUser(account.id) ?: authInvalidCredentials()

    return AuthenticatedIdentity(user = user, loginAccount = account)
  }
}
