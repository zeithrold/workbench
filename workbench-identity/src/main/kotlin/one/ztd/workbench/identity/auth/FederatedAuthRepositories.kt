package one.ztd.workbench.identity.auth

import kotlinx.serialization.json.JsonObject
import one.ztd.workbench.identity.LoginAccountStore
import one.ztd.workbench.identity.LoginMethodRepository
import one.ztd.workbench.identity.TenantLoginMethodSettingRepository
import one.ztd.workbench.identity.UserLoginAccountRepository
import one.ztd.workbench.identity.model.LoginMethodKind
import one.ztd.workbench.tenant.TenantRepository
import org.springframework.stereotype.Component

@Component
class FederatedAuthRepositories(
  val loginMethods: LoginMethodRepository,
  val tenantLoginSettings: TenantLoginMethodSettingRepository,
  val loginAccounts: LoginAccountStore,
  val userLoginAccounts: UserLoginAccountRepository,
  val tenants: TenantRepository,
  val loginStates: AuthLoginStateRepository,
)

@Component
class FederatedAuthClients(
  val oauth: OAuthFederatedClient,
  val saml: SamlFederatedClient,
)

interface OAuthFederatedClient {
  fun pkceChallenge(verifier: String): String

  fun buildAuthorizeUrl(
    config: JsonObject,
    redirectUri: String,
    state: String,
    challenge: String,
    kind: LoginMethodKind,
  ): String

  fun exchangeAuthorizationCode(
    config: JsonObject,
    secretRef: String?,
    code: String,
    redirectUri: String,
    verifier: String?,
  ): JsonObject

  fun resolveSubject(
    config: JsonObject,
    tokenResponse: JsonObject,
    kind: LoginMethodKind,
  ): String
}

interface SamlFederatedClient {
  fun buildAuthorizeUrl(config: JsonObject, redirectUri: String, relayState: String): String

  fun parseNameId(samlResponse: String): String
}
