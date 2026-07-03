@file:Suppress("UnusedParameter", "ThrowsCount")

package doa.ink.workbench.service.identity.auth

import doa.ink.workbench.core.common.errors.InvalidRequestException
import doa.ink.workbench.core.identity.LoginAccountRepository
import doa.ink.workbench.core.identity.TenantRepository
import doa.ink.workbench.core.identity.auth.AuthLoginStateRepository
import doa.ink.workbench.core.identity.auth.CredentialHasher
import doa.ink.workbench.core.identity.auth.CredentialSecretGenerator
import doa.ink.workbench.core.identity.model.AuthenticatedIdentity
import doa.ink.workbench.core.identity.model.CreateAuthLoginStateCommand
import doa.ink.workbench.core.identity.model.LoginMethodKind
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import kotlinx.serialization.json.JsonObject
import org.springframework.stereotype.Service

@Service
class FederatedAuthService(
  private val loginAccounts: LoginAccountRepository,
  private val tenants: TenantRepository,
  private val loginStates: AuthLoginStateRepository,
  private val secretGenerator: CredentialSecretGenerator,
  private val credentialHasher: CredentialHasher,
  private val oauthClient: OAuthFederatedClient,
  private val samlClient: SamlFederatedClient,
  private val clock: Clock,
) {
  private val stateTtl = Duration.ofMinutes(10)

  suspend fun beginAuthorize(
    loginMethodId: String,
    tenantId: String,
    returnUrl: String,
    redirectUri: String,
  ): FederatedAuthorizeResult {
    val tenant =
      tenants.findByApiId(tenantId) ?: throw InvalidRequestException("Unknown tenant: $tenantId")
    val method =
      loginAccounts.findLoginMethodByApiId(loginMethodId)
        ?: throw InvalidRequestException("Unknown login method: $loginMethodId")
    if (method.kind !in setOf(LoginMethodKind.OAUTH2, LoginMethodKind.OIDC, LoginMethodKind.SAML)) {
      throw InvalidRequestException(
        "Login method $loginMethodId does not support federated authorize."
      )
    }
    val setting = loginAccounts.findTenantSetting(tenant.id, method.id)
    if (setting?.isEnabled != true) {
      throw InvalidRequestException("Login method is disabled for this tenant.")
    }

    val state = secretGenerator.generate()
    val verifier = secretGenerator.generate()
    val challenge = oauthClient.pkceChallenge(verifier)
    val now = OffsetDateTime.now(clock)
    loginStates.create(
      CreateAuthLoginStateCommand(
        stateHash = credentialHasher.hash(state),
        tenantId = tenant.id,
        loginMethodId = method.id,
        redirectUri = redirectUri,
        pkceVerifier = verifier,
        returnUrl = returnUrl,
        expiresAt = now.plus(stateTtl),
      )
    )

    val config = setting.config as? JsonObject ?: JsonObject(emptyMap())
    val authorizationUrl =
      when (method.kind) {
        LoginMethodKind.SAML -> samlClient.buildAuthorizeUrl(config, redirectUri, state)
        LoginMethodKind.OAUTH2,
        LoginMethodKind.OIDC ->
          oauthClient.buildAuthorizeUrl(config, redirectUri, state, challenge, method.kind)
        else -> throw InvalidRequestException("Login method does not support federated authorize.")
      }
    return FederatedAuthorizeResult(authorizationUrl = authorizationUrl, state = state)
  }

  suspend fun completeOAuthCallback(
    code: String,
    state: String,
    redirectUri: String,
  ): AuthenticatedIdentity {
    val now = OffsetDateTime.now(clock)
    val loginState =
      loginStates.findActiveByStateHash(credentialHasher.hash(state), now)
        ?: throw InvalidRequestException("OAuth state is invalid or expired.")
    loginStates.consume(loginState.id, now)

    val methodRecord =
      loginAccounts.findLoginMethodById(loginState.loginMethodId)
        ?: throw InvalidRequestException("Login method no longer exists.")
    val setting =
      loginAccounts.findTenantSetting(loginState.tenantId, loginState.loginMethodId)
        ?: throw InvalidRequestException("Tenant login settings not found.")
    val config = setting.config as? JsonObject ?: JsonObject(emptyMap())
    val tokenResponse =
      oauthClient.exchangeAuthorizationCode(
        config,
        setting.secretRef,
        code,
        redirectUri,
        loginState.pkceVerifier,
      )
    val subject = oauthClient.resolveSubject(config, tokenResponse, methodRecord.kind)
    return resolveIdentity(methodRecord.code, subject, "federated")
  }

  suspend fun completeSamlAcs(samlResponse: String, relayState: String): AuthenticatedIdentity {
    val now = OffsetDateTime.now(clock)
    val loginState =
      loginStates.findActiveByStateHash(credentialHasher.hash(relayState), now)
        ?: throw InvalidRequestException("SAML relay state is invalid or expired.")
    loginStates.consume(loginState.id, now)
    val methodRecord =
      loginAccounts.findLoginMethodById(loginState.loginMethodId)
        ?: throw InvalidRequestException("Login method no longer exists.")
    val subject = SamlResponseParser.parseNameId(samlResponse)
    return resolveIdentity(methodRecord.code, subject, "SAML")
  }

  private suspend fun resolveIdentity(
    methodCode: String,
    subject: String,
    protocol: String,
  ): AuthenticatedIdentity {
    val account =
      loginAccounts.findLoginAccountByMethodAndSubject(methodCode, normalizeSubject(subject))
        ?: throw InvalidRequestException("No linked account for $protocol identity.")
    val user =
      loginAccounts.findLinkedUser(account.id)
        ?: throw InvalidRequestException("No user linked for $protocol identity.")
    return AuthenticatedIdentity(user = user, loginAccount = account)
  }
}

data class FederatedAuthorizeResult(val authorizationUrl: String, val state: String)
