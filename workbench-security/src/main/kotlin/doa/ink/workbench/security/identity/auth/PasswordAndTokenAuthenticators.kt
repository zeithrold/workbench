@file:Suppress("ThrowsCount")

package doa.ink.workbench.security.identity.auth

import doa.ink.workbench.core.common.errors.AuthenticationFailedException
import doa.ink.workbench.core.common.errors.InvalidRequestException
import doa.ink.workbench.core.common.errors.WorkbenchErrorCode
import doa.ink.workbench.core.identity.LoginAccountStore
import doa.ink.workbench.core.identity.LoginMethodRepository
import doa.ink.workbench.core.identity.TenantLoginMethodSettingRepository
import doa.ink.workbench.core.identity.TenantRepository
import doa.ink.workbench.core.identity.UserLoginAccountRepository
import doa.ink.workbench.core.identity.auth.PasswordVerifier
import doa.ink.workbench.core.identity.model.AuthenticatedIdentity
import doa.ink.workbench.core.identity.model.LoginAccountParameterKey
import doa.ink.workbench.core.identity.model.LoginCommand
import doa.ink.workbench.core.identity.model.LoginMethodKind
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
    val subject =
      command.subject
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.IDENTITY_LOGIN_SUBJECT_REQUIRED,
          "subject is required for password login.",
        )
    val password =
      command.password
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.IDENTITY_LOGIN_PASSWORD_REQUIRED,
          "password is required for password login.",
        )
    val normalizedSubject = normalizeSubject(subject)
    val methodCode =
      command.loginMethodId?.let { apiId ->
        loginMethods.findLoginMethodByApiId(apiId)?.code
      } ?: PASSWORD_METHOD_CODE
    val account =
      loginAccounts.findLoginAccountByMethodAndSubject(methodCode, normalizedSubject)
        ?: throw AuthenticationFailedException(WorkbenchErrorCode.AUTH_INVALID_CREDENTIALS)

    val passwordHash =
      loginAccounts.findParameter(account.id, LoginAccountParameterKey.PasswordHash)?.parameterValue
        ?: throw AuthenticationFailedException(WorkbenchErrorCode.AUTH_INVALID_CREDENTIALS)

    if (!passwordVerifier.verify(password, passwordHash)) {
      throw AuthenticationFailedException(WorkbenchErrorCode.AUTH_INVALID_CREDENTIALS)
    }

    val user =
      userLoginAccounts.findLinkedUser(account.id)
        ?: throw AuthenticationFailedException(WorkbenchErrorCode.AUTH_INVALID_CREDENTIALS)

    return AuthenticatedIdentity(user = user, loginAccount = account)
  }
}

@Component
class ApiTokenLoginAuthenticator(
  private val loginMethods: LoginMethodRepository,
  private val loginAccounts: LoginAccountStore,
  private val userLoginAccounts: UserLoginAccountRepository,
  private val credentialHasher: doa.ink.workbench.core.identity.auth.CredentialHasher,
) : LoginAuthenticator {
  override val kind: LoginMethodKind = LoginMethodKind.API_TOKEN

  override suspend fun authenticate(command: LoginCommand): AuthenticatedIdentity {
    val token =
      command.token
        ?: throw InvalidRequestException(WorkbenchErrorCode.IDENTITY_LOGIN_TOKEN_REQUIRED)
    val methodCode =
      command.loginMethodId?.let { loginMethods.findLoginMethodByApiId(it)?.code } ?: "api_token"
    val tokenHash = credentialHasher.hash(token)

    val account =
      loginAccounts.findLoginAccountByParameterValue(
        loginMethodCode = methodCode,
        parameterKey = LoginAccountParameterKey.ApiTokenHash,
        parameterValue = tokenHash,
      ) ?: throw AuthenticationFailedException(WorkbenchErrorCode.AUTH_INVALID_CREDENTIALS)

    val user =
      userLoginAccounts.findLinkedUser(account.id)
        ?: throw AuthenticationFailedException(WorkbenchErrorCode.AUTH_INVALID_CREDENTIALS)

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
    val loginMethodId =
      command.loginMethodId
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.IDENTITY_LOGIN_METHOD_ID_REQUIRED,
          "loginMethodId is required for ldap login.",
        )
    val tenantId =
      command.tenantId
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.IDENTITY_LOGIN_TENANT_ID_REQUIRED,
          "tenantId is required for ldap login.",
        )
    val subject =
      command.subject
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.IDENTITY_LOGIN_SUBJECT_REQUIRED,
          "subject is required for ldap login.",
        )
    val password =
      command.password
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.IDENTITY_LOGIN_PASSWORD_REQUIRED,
          "password is required for ldap login.",
        )

    val tenant =
      tenants.findByApiId(tenantId)
        ?: throw AuthenticationFailedException(WorkbenchErrorCode.AUTH_INVALID_CREDENTIALS)
    val method =
      loginMethods.findLoginMethodByApiId(loginMethodId)
        ?: throw AuthenticationFailedException(WorkbenchErrorCode.AUTH_INVALID_CREDENTIALS)
    if (method.kind != LoginMethodKind.LDAP) {
      throw InvalidRequestException(
        WorkbenchErrorCode.IDENTITY_LOGIN_METHOD_NOT_LDAP,
        "Login method $loginMethodId is not LDAP.",
      )
    }
    val setting = tenantLoginSettings.findTenantSetting(tenant.id, method.id)
    if (setting?.isEnabled != true) {
      throw AuthenticationFailedException(WorkbenchErrorCode.AUTH_INVALID_CREDENTIALS)
    }

    val normalizedSubject = ldapClient.authenticate(setting, subject, password)
    val account =
      loginAccounts.findLoginAccountByMethodAndSubject(method.code, normalizedSubject)
        ?: throw AuthenticationFailedException(WorkbenchErrorCode.AUTH_INVALID_CREDENTIALS)
    val user =
      userLoginAccounts.findLinkedUser(account.id)
        ?: throw AuthenticationFailedException(WorkbenchErrorCode.AUTH_INVALID_CREDENTIALS)

    return AuthenticatedIdentity(user = user, loginAccount = account)
  }
}
