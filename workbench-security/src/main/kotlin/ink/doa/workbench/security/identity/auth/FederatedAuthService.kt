@file:Suppress("UnusedParameter")

package ink.doa.workbench.security.identity.auth

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.identity.LoginAccountStore
import ink.doa.workbench.core.identity.LoginMethodRepository
import ink.doa.workbench.core.identity.TenantLoginMethodSettingRepository
import ink.doa.workbench.core.identity.TenantRepository
import ink.doa.workbench.core.identity.UserLoginAccountRepository
import ink.doa.workbench.core.identity.auth.AuthLoginStateRepository
import ink.doa.workbench.core.identity.auth.CredentialHasher
import ink.doa.workbench.core.identity.auth.CredentialSecretGenerator
import ink.doa.workbench.core.identity.model.AuthenticatedIdentity
import ink.doa.workbench.core.identity.model.CreateAuthLoginStateCommand
import ink.doa.workbench.core.identity.model.LoginMethodKind
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import org.springframework.stereotype.Service

@Service
class FederatedAuthService(
  private val loginMethods: LoginMethodRepository,
  private val tenantLoginSettings: TenantLoginMethodSettingRepository,
  private val loginAccounts: LoginAccountStore,
  private val userLoginAccounts: UserLoginAccountRepository,
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
    val tenant = requireTenantByApiId(tenants, tenantId)
    val method = requireLoginMethodByApiId(loginMethods, loginMethodId)
    requireFederatedMethod(method, loginMethodId)
    val setting =
      requireEnabledFederatedSetting(tenantLoginSettings.findTenantSetting(tenant.id, method.id))

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
        else ->
          throw InvalidRequestException(WorkbenchErrorCode.IDENTITY_FEDERATED_PROTOCOL_UNSUPPORTED)
      }
    return FederatedAuthorizeResult(authorizationUrl = authorizationUrl, state = state)
  }

  suspend fun completeOAuthCallback(
    code: String,
    state: String,
    redirectUri: String,
  ): FederatedLoginResult {
    val context = loadOAuthCallbackContext(state)
    val config = context.setting.config as? JsonObject ?: JsonObject(emptyMap())
    val tokenResponse =
      oauthClient.exchangeAuthorizationCode(
        config,
        context.setting.secretRef,
        code,
        redirectUri,
        context.loginState.pkceVerifier,
      )
    val subject = oauthClient.resolveSubject(config, tokenResponse, context.methodRecord.kind)
    return FederatedLoginResult(
      identity = resolveIdentity(context.methodRecord.code, subject, "federated"),
      tenantId = context.loginState.tenantId,
    )
  }

  private suspend fun loadOAuthCallbackContext(state: String): OAuthCallbackContext {
    val now = OffsetDateTime.now(clock)
    val loginState = requireActiveOAuthLoginState(loginStates, credentialHasher.hash(state), now)
    loginStates.consume(loginState.id, now)
    val methodRecord = requireLoginMethodByInternalId(loginMethods, loginState.loginMethodId)
    val setting =
      requireTenantLoginSetting(
        tenantLoginSettings,
        loginState.tenantId,
        loginState.loginMethodId,
      )
    return OAuthCallbackContext(loginState, methodRecord, setting)
  }

  suspend fun completeSamlAcs(samlResponse: String, relayState: String): FederatedLoginResult {
    val now = OffsetDateTime.now(clock)
    val loginState =
      loginStates.findActiveByStateHash(credentialHasher.hash(relayState), now)
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.IDENTITY_FEDERATED_SAML_RELAY_STATE_INVALID
        )
    loginStates.consume(loginState.id, now)
    val methodRecord =
      loginMethods.findLoginMethodById(loginState.loginMethodId)
        ?: throw InvalidRequestException(WorkbenchErrorCode.RESOURCE_LOGIN_METHOD_NOT_FOUND)
    val subject = SamlResponseParser.parseNameId(samlResponse)
    return FederatedLoginResult(
      identity = resolveIdentity(methodRecord.code, subject, "SAML"),
      tenantId = loginState.tenantId,
    )
  }

  private suspend fun resolveIdentity(
    methodCode: String,
    subject: String,
    protocol: String,
  ): AuthenticatedIdentity {
    val account =
      loginAccounts.findLoginAccountByMethodAndSubject(methodCode, normalizeSubject(subject))
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.IDENTITY_FEDERATED_LINKED_ACCOUNT_NOT_FOUND,
          "No linked account for $protocol identity.",
        )
    val user =
      userLoginAccounts.findLinkedUser(account.id)
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.IDENTITY_FEDERATED_LINKED_USER_NOT_FOUND,
          "No user linked for $protocol identity.",
        )
    return AuthenticatedIdentity(user = user, loginAccount = account)
  }
}

data class FederatedAuthorizeResult(val authorizationUrl: String, val state: String)

data class FederatedLoginResult(
  val identity: AuthenticatedIdentity,
  val tenantId: UUID,
)

private data class OAuthCallbackContext(
  val loginState: ink.doa.workbench.core.identity.model.AuthLoginStateRecord,
  val methodRecord: ink.doa.workbench.core.identity.model.LoginMethodDefinitionRecord,
  val setting: ink.doa.workbench.core.identity.model.TenantLoginMethodSettingRecord,
)
