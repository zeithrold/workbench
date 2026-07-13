package ink.doa.workbench.identity.auth

import ink.doa.workbench.identity.model.AuthenticatedIdentity
import ink.doa.workbench.identity.model.CreateAuthLoginStateCommand
import ink.doa.workbench.identity.model.LoginMethodKind
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import org.springframework.stereotype.Service

@Service
class FederatedAuthService(
  private val repositories: FederatedAuthRepositories,
  private val clients: FederatedAuthClients,
  private val crypto: CredentialCryptoSupport,
  private val clock: Clock,
) {
  private val stateTtl = Duration.ofMinutes(10)

  suspend fun beginAuthorize(
    loginMethodId: String,
    tenantId: String,
    returnUrl: String,
    redirectUri: String,
  ): FederatedAuthorizeResult {
    val tenant = requireTenantByApiId(repositories.tenants, tenantId)
    val method = requireLoginMethodByApiId(repositories.loginMethods, loginMethodId)
    requireFederatedMethod(method, loginMethodId)
    val setting =
      requireEnabledFederatedSetting(
        repositories.tenantLoginSettings.findTenantSetting(tenant.id, method.id)
      )

    val state = crypto.secretGenerator.generate()
    val verifier = crypto.secretGenerator.generate()
    val challenge = clients.oauth.pkceChallenge(verifier)
    val now = OffsetDateTime.now(clock)
    repositories.loginStates.create(
      CreateAuthLoginStateCommand(
        stateHash = crypto.credentialHasher.hash(state),
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
        LoginMethodKind.SAML -> clients.saml.buildAuthorizeUrl(config, redirectUri, state)
        LoginMethodKind.OAUTH2,
        LoginMethodKind.OIDC ->
          clients.oauth.buildAuthorizeUrl(config, redirectUri, state, challenge, method.kind)
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
      clients.oauth.exchangeAuthorizationCode(
        config,
        context.setting.secretRef,
        code,
        redirectUri,
        context.loginState.pkceVerifier,
      )
    val subject = clients.oauth.resolveSubject(config, tokenResponse, context.methodRecord.kind)
    return FederatedLoginResult(
      identity = resolveIdentity(context.methodRecord.code, subject, "federated"),
      tenantId = context.loginState.tenantId,
    )
  }

  private suspend fun loadOAuthCallbackContext(state: String): OAuthCallbackContext {
    val now = OffsetDateTime.now(clock)
    val loginState =
      requireActiveOAuthLoginState(
        repositories.loginStates,
        crypto.credentialHasher.hash(state),
        now,
      )
    repositories.loginStates.consume(loginState.id, now)
    val methodRecord =
      requireLoginMethodByInternalId(repositories.loginMethods, loginState.loginMethodId)
    val setting =
      requireTenantLoginSetting(
        repositories.tenantLoginSettings,
        loginState.tenantId,
        loginState.loginMethodId,
      )
    return OAuthCallbackContext(loginState, methodRecord, setting)
  }

  suspend fun completeSamlAcs(samlResponse: String, relayState: String): FederatedLoginResult {
    val now = OffsetDateTime.now(clock)
    val loginState =
      repositories.loginStates.findActiveByStateHash(crypto.credentialHasher.hash(relayState), now)
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.IDENTITY_FEDERATED_SAML_RELAY_STATE_INVALID
        )
    repositories.loginStates.consume(loginState.id, now)
    val methodRecord =
      repositories.loginMethods.findLoginMethodById(loginState.loginMethodId)
        ?: throw InvalidRequestException(WorkbenchErrorCode.RESOURCE_LOGIN_METHOD_NOT_FOUND)
    val subject = clients.saml.parseNameId(samlResponse)
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
      repositories.loginAccounts.findLoginAccountByMethodAndSubject(
        methodCode,
        normalizeSubject(subject),
      )
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.IDENTITY_FEDERATED_LINKED_ACCOUNT_NOT_FOUND,
          "No linked account for $protocol identity.",
        )
    val user =
      repositories.userLoginAccounts.findLinkedUser(account.id)
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
  val loginState: ink.doa.workbench.identity.model.AuthLoginStateRecord,
  val methodRecord: ink.doa.workbench.identity.model.LoginMethodDefinitionRecord,
  val setting: ink.doa.workbench.identity.model.TenantLoginMethodSettingRecord,
)
