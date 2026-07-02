@file:Suppress("ThrowsCount")

package doa.ink.workbench.service.identity.auth

import doa.ink.workbench.core.common.errors.AuthenticationFailedException
import doa.ink.workbench.core.common.errors.InvalidRequestException
import doa.ink.workbench.core.identity.LoginAccountRepository
import doa.ink.workbench.core.identity.TenantRepository
import doa.ink.workbench.core.identity.auth.PasswordVerifier
import doa.ink.workbench.core.identity.model.AuthenticatedIdentity
import doa.ink.workbench.core.identity.model.LoginAccountParameterKey
import doa.ink.workbench.core.identity.model.LoginCommand
import doa.ink.workbench.core.identity.model.LoginMethodKind
import org.springframework.stereotype.Component

private const val PASSWORD_METHOD_CODE = "password"

@Component
class PasswordLoginAuthenticator(
  private val loginAccounts: LoginAccountRepository,
  private val passwordVerifier: PasswordVerifier,
) : LoginAuthenticator {
  override val kind: LoginMethodKind = LoginMethodKind.PASSWORD

  override suspend fun authenticate(command: LoginCommand): AuthenticatedIdentity {
    val subject =
      command.subject ?: throw InvalidRequestException("subject is required for password login.")
    val password =
      command.password ?: throw InvalidRequestException("password is required for password login.")
    val normalizedSubject = normalizeSubject(subject)
    val account =
      loginAccounts.findLoginAccountByMethodAndSubject(PASSWORD_METHOD_CODE, normalizedSubject)
        ?: throw AuthenticationFailedException("Invalid credentials.")

    val passwordHash =
      loginAccounts.findParameter(account.id, LoginAccountParameterKey.PasswordHash)?.parameterValue
        ?: throw AuthenticationFailedException("Invalid credentials.")

    if (!passwordVerifier.verify(password, passwordHash)) {
      throw AuthenticationFailedException("Invalid credentials.")
    }

    val user =
      loginAccounts.findLinkedUser(account.id)
        ?: throw AuthenticationFailedException("Invalid credentials.")

    return AuthenticatedIdentity(user = user, loginAccount = account)
  }
}

@Component
class ApiTokenLoginAuthenticator(
  private val loginAccounts: LoginAccountRepository,
  private val credentialHasher: doa.ink.workbench.core.identity.auth.CredentialHasher,
) : LoginAuthenticator {
  override val kind: LoginMethodKind = LoginMethodKind.API_TOKEN

  override suspend fun authenticate(command: LoginCommand): AuthenticatedIdentity {
    val token =
      command.token ?: throw InvalidRequestException("token is required for api_token login.")
    val methodCode = command.loginMethodCode ?: "api_token"
    val tokenHash = credentialHasher.hash(token)

    val account =
      loginAccounts.findLoginAccountByParameterValue(
        loginMethodCode = methodCode,
        parameterKey = LoginAccountParameterKey.ApiTokenHash,
        parameterValue = tokenHash,
      ) ?: throw AuthenticationFailedException("Invalid credentials.")

    val user =
      loginAccounts.findLinkedUser(account.id)
        ?: throw AuthenticationFailedException("Invalid credentials.")

    return AuthenticatedIdentity(user = user, loginAccount = account)
  }
}

@Component
class LdapLoginAuthenticator(
  private val loginAccounts: LoginAccountRepository,
  private val tenants: TenantRepository,
  private val ldapClient: LdapAuthClient,
) : LoginAuthenticator {
  override val kind: LoginMethodKind = LoginMethodKind.LDAP

  override suspend fun authenticate(command: LoginCommand): AuthenticatedIdentity {
    val methodCode =
      command.loginMethodCode
        ?: throw InvalidRequestException("loginMethodCode is required for ldap login.")
    val tenantApiId =
      command.tenantApiId
        ?: throw InvalidRequestException("tenantApiId is required for ldap login.")
    val subject =
      command.subject ?: throw InvalidRequestException("subject is required for ldap login.")
    val password =
      command.password ?: throw InvalidRequestException("password is required for ldap login.")

    val tenant =
      tenants.findByApiId(tenantApiId)
        ?: throw AuthenticationFailedException("Invalid credentials.")
    val method =
      loginAccounts.findLoginMethodByCode(methodCode)
        ?: throw AuthenticationFailedException("Invalid credentials.")
    if (method.kind != LoginMethodKind.LDAP) {
      throw InvalidRequestException("Login method $methodCode is not LDAP.")
    }
    val setting = loginAccounts.findTenantSetting(tenant.id, method.id)
    if (setting?.isEnabled != true) {
      throw AuthenticationFailedException("Invalid credentials.")
    }

    val normalizedSubject = ldapClient.authenticate(setting, subject, password)
    val account =
      loginAccounts.findLoginAccountByMethodAndSubject(methodCode, normalizedSubject)
        ?: throw AuthenticationFailedException("Invalid credentials.")
    val user =
      loginAccounts.findLinkedUser(account.id)
        ?: throw AuthenticationFailedException("Invalid credentials.")

    return AuthenticatedIdentity(user = user, loginAccount = account)
  }
}
