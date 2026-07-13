package ink.doa.workbench.identity.auth

import ink.doa.workbench.identity.LoginAccountStore
import ink.doa.workbench.identity.LoginMethodRepository
import ink.doa.workbench.identity.TenantLoginMethodSettingRepository
import ink.doa.workbench.identity.UserLoginAccountRepository
import ink.doa.workbench.identity.model.LoginMethodKind
import ink.doa.workbench.tenant.TenantRepository
import kotlinx.serialization.json.JsonObject
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
