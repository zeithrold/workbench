package ink.doa.workbench.security.identity.auth

import dasniko.testcontainers.keycloak.KeycloakContainer
import ink.doa.workbench.data.repository.identity.ExposedAuthLoginStateRepository
import ink.doa.workbench.data.repository.identity.ExposedLoginAccountStore
import ink.doa.workbench.data.repository.identity.ExposedLoginMethodRepository
import ink.doa.workbench.data.repository.identity.ExposedTenantLoginMethodSettingRepository
import ink.doa.workbench.data.repository.identity.ExposedTenantRepository
import ink.doa.workbench.data.repository.identity.ExposedUserLoginAccountRepository
import ink.doa.workbench.security.identity.auth.support.AuthIntegrationFixtures
import ink.doa.workbench.security.identity.auth.support.FederatedAuthFixture
import ink.doa.workbench.security.identity.auth.support.KeycloakTestContainer
import ink.doa.workbench.security.identity.auth.support.MapSecretResolver
import ink.doa.workbench.security.identity.auth.support.OAuthAuthorizationCodeClient
import ink.doa.workbench.testsupport.postgres.PostgresTestDatabaseLease
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Clock
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.Tag

@Tag("integration")
class OAuth2AuthIntegrationTest :
  StringSpec({
    val keycloak: KeycloakContainer = KeycloakTestContainer.shared()
    val postgresLease: PostgresTestDatabaseLease = AuthIntegrationFixtures.openSpecDatabase()
    lateinit var database: Database
    lateinit var fixture: FederatedAuthFixture
    lateinit var federatedAuthService: FederatedAuthService

    beforeSpec {
      database = postgresLease.database
      fixture = runBlocking { AuthIntegrationFixtures.seedFederatedFixture(database, keycloak) }
      val secretResolver = MapSecretResolver(AuthIntegrationFixtures.keycloakSecrets())
      val loginMethods = ExposedLoginMethodRepository(database)
      val tenantLoginSettings = ExposedTenantLoginMethodSettingRepository(database)
      val loginAccounts = ExposedLoginAccountStore(database)
      val userLoginAccounts = ExposedUserLoginAccountRepository(database)
      federatedAuthService =
        FederatedAuthService(
          repositories =
            FederatedAuthRepositories(
              loginMethods = loginMethods,
              tenantLoginSettings = tenantLoginSettings,
              loginAccounts = loginAccounts,
              userLoginAccounts = userLoginAccounts,
              tenants = ExposedTenantRepository(database),
              loginStates = ExposedAuthLoginStateRepository(database),
            ),
          clients =
            FederatedAuthClients(
              oauth = OAuthFederatedClient(secretResolver),
              saml = SamlFederatedClient(),
            ),
          crypto =
            CredentialCryptoSupport(
              secretGenerator = SecureRandomCredentialSecretGenerator(),
              credentialHasher = Sha256CredentialHasher(),
            ),
          clock = Clock.systemUTC(),
        )
    }

    afterSpec {
      postgresLease.close()
    }

    "beginAuthorize uses configured oauth2 scope" {
      runBlocking {
        val result =
          federatedAuthService.beginAuthorize(
            loginMethodId = fixture.oauth2LoginMethodApiId,
            tenantId = fixture.tenant.tenantApiId,
            returnUrl = "/",
            redirectUri = KeycloakTestContainer.REDIRECT_URI,
          )
        val scope = OAuthAuthorizationCodeClient.scopeQueryValue(result.authorizationUrl)
        scope shouldBe "email profile openid"
      }
    }

    "completeOAuthCallback resolves subject from userinfo endpoint" {
      runBlocking {
        val authorize =
          federatedAuthService.beginAuthorize(
            loginMethodId = fixture.oauth2LoginMethodApiId,
            tenantId = fixture.tenant.tenantApiId,
            returnUrl = "/",
            redirectUri = KeycloakTestContainer.REDIRECT_URI,
          )
        val code =
          OAuthAuthorizationCodeClient.obtainAuthorizationCode(
            authorizationUrl = authorize.authorizationUrl,
            username = KeycloakTestContainer.OAUTH2_USER,
            password = KeycloakTestContainer.OAUTH2_PASSWORD,
            redirectUri = KeycloakTestContainer.REDIRECT_URI,
          )
        val result =
          federatedAuthService.completeOAuthCallback(
            code = code,
            state = authorize.state,
            redirectUri = KeycloakTestContainer.REDIRECT_URI,
          )
        result.identity.user.id shouldBe fixture.oauth2UserId
      }
    }
  })
