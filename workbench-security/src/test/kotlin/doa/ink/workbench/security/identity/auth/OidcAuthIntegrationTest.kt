package doa.ink.workbench.security.identity.auth

import dasniko.testcontainers.keycloak.KeycloakContainer
import doa.ink.workbench.core.common.errors.InvalidRequestException
import doa.ink.workbench.data.identity.ExposedAuthLoginStateRepository
import doa.ink.workbench.data.identity.ExposedLoginAccountStore
import doa.ink.workbench.data.identity.ExposedLoginMethodRepository
import doa.ink.workbench.data.identity.ExposedTenantLoginMethodSettingRepository
import doa.ink.workbench.data.identity.ExposedTenantRepository
import doa.ink.workbench.data.identity.ExposedUserLoginAccountRepository
import doa.ink.workbench.security.identity.auth.support.AuthIntegrationFixtures
import doa.ink.workbench.security.identity.auth.support.FederatedAuthFixture
import doa.ink.workbench.security.identity.auth.support.KeycloakTestContainer
import doa.ink.workbench.security.identity.auth.support.MapSecretResolver
import doa.ink.workbench.security.identity.auth.support.OAuthAuthorizationCodeClient
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.Clock
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.Tag
import org.testcontainers.containers.PostgreSQLContainer

@Tag("integration")
class OidcAuthIntegrationTest :
  StringSpec({
    val keycloak: KeycloakContainer = KeycloakTestContainer.create()
    val postgres: PostgreSQLContainer<*> = AuthIntegrationFixtures.startPostgres()
    lateinit var database: Database
    lateinit var fixture: FederatedAuthFixture
    lateinit var federatedAuthService: FederatedAuthService

    beforeSpec {
      keycloak.start()
      database = AuthIntegrationFixtures.connectDatabase(postgres)
      fixture = runBlocking { AuthIntegrationFixtures.seedFederatedFixture(database, keycloak) }
      val secretResolver = MapSecretResolver(AuthIntegrationFixtures.keycloakSecrets())
      val loginMethods = ExposedLoginMethodRepository(database)
      val tenantLoginSettings = ExposedTenantLoginMethodSettingRepository(database)
      val loginAccounts = ExposedLoginAccountStore(database)
      val userLoginAccounts = ExposedUserLoginAccountRepository(database)
      federatedAuthService =
        FederatedAuthService(
          loginMethods = loginMethods,
          tenantLoginSettings = tenantLoginSettings,
          loginAccounts = loginAccounts,
          userLoginAccounts = userLoginAccounts,
          tenants = ExposedTenantRepository(database),
          loginStates = ExposedAuthLoginStateRepository(database),
          secretGenerator = SecureRandomCredentialSecretGenerator(),
          credentialHasher = Sha256CredentialHasher(),
          oauthClient = OAuthFederatedClient(secretResolver),
          samlClient = SamlFederatedClient(),
          clock = Clock.systemUTC(),
        )
    }

    afterSpec {
      keycloak.stop()
      postgres.stop()
    }

    "beginAuthorize returns a Keycloak authorization URL with PKCE parameters" {
      runBlocking {
        val result =
          federatedAuthService.beginAuthorize(
            loginMethodId = fixture.oidcLoginMethodApiId,
            tenantId = fixture.tenant.tenantApiId,
            returnUrl = "/",
            redirectUri = KeycloakTestContainer.REDIRECT_URI,
          )
        result.authorizationUrl shouldContain KeycloakTestContainer.authorizationEndpoint(keycloak)
        result.authorizationUrl shouldContain "code_challenge="
        result.authorizationUrl shouldContain "code_challenge_method=S256"
        result.state.isNotBlank() shouldBe true
      }
    }

    "completeOAuthCallback resolves subject from id_token and returns linked identity" {
      runBlocking {
        val authorize =
          federatedAuthService.beginAuthorize(
            loginMethodId = fixture.oidcLoginMethodApiId,
            tenantId = fixture.tenant.tenantApiId,
            returnUrl = "/",
            redirectUri = KeycloakTestContainer.REDIRECT_URI,
          )
        val code =
          OAuthAuthorizationCodeClient.obtainAuthorizationCode(
            authorizationUrl = authorize.authorizationUrl,
            username = KeycloakTestContainer.OIDC_USER,
            password = KeycloakTestContainer.OIDC_PASSWORD,
            redirectUri = KeycloakTestContainer.REDIRECT_URI,
          )
        val result =
          federatedAuthService.completeOAuthCallback(
            code = code,
            state = authorize.state,
            redirectUri = KeycloakTestContainer.REDIRECT_URI,
          )
        result.identity.user.id shouldBe fixture.oidcUserId
      }
    }

    "completeOAuthCallback rejects invalid state" {
      runBlocking {
        shouldThrow<InvalidRequestException> {
          federatedAuthService.completeOAuthCallback(
            code = "invalid-code",
            state = "invalid-state",
            redirectUri = KeycloakTestContainer.REDIRECT_URI,
          )
        }
      }
    }
  })
