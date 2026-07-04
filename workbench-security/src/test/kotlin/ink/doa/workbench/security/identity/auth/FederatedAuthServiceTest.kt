package ink.doa.workbench.security.identity.auth

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.LoginAccountStore
import ink.doa.workbench.core.identity.LoginMethodRepository
import ink.doa.workbench.core.identity.TenantLoginMethodSettingRepository
import ink.doa.workbench.core.identity.TenantRepository
import ink.doa.workbench.core.identity.UserLoginAccountRepository
import ink.doa.workbench.core.identity.auth.AuthLoginStateRepository
import ink.doa.workbench.core.identity.auth.CredentialHasher
import ink.doa.workbench.core.identity.auth.CredentialSecretGenerator
import ink.doa.workbench.core.identity.model.LoginMethodDefinitionRecord
import ink.doa.workbench.core.identity.model.LoginMethodKind
import ink.doa.workbench.core.identity.model.TenantLoginMethodSettingRecord
import ink.doa.workbench.core.identity.model.TenantRecord
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

class FederatedAuthServiceTest :
  StringSpec({
    val loginMethods = mockk<LoginMethodRepository>()
    val tenantLoginSettings = mockk<TenantLoginMethodSettingRepository>()
    val loginAccounts = mockk<LoginAccountStore>(relaxed = true)
    val userLoginAccounts = mockk<UserLoginAccountRepository>(relaxed = true)
    val tenants = mockk<TenantRepository>()
    val loginStates = mockk<AuthLoginStateRepository>(relaxed = true)
    val secretGenerator = mockk<CredentialSecretGenerator>()
    val credentialHasher = mockk<CredentialHasher>(relaxed = true)
    val oauthClient = mockk<OAuthFederatedClient>()
    val samlClient = mockk<SamlFederatedClient>()
    val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
    val service =
      FederatedAuthService(
        loginMethods,
        tenantLoginSettings,
        loginAccounts,
        userLoginAccounts,
        tenants,
        loginStates,
        secretGenerator,
        credentialHasher,
        oauthClient,
        samlClient,
        clock,
      )

    val tenantId = UUID.randomUUID()
    val methodId = UUID.randomUUID()
    val tenant =
      TenantRecord(
        id = tenantId,
        apiId = PublicId.new("ten"),
        slug = "acme",
        name = "Acme",
        timezone = "UTC",
        locale = "en-US",
        createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
      )
    val oidcMethod =
      LoginMethodDefinitionRecord(
        id = methodId,
        apiId = PublicId.new("lmd"),
        code = "oidc",
        kind = LoginMethodKind.OIDC,
        name = "OIDC",
        isBuiltin = false,
        isEnabledGlobally = true,
        createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
      )
    val setting =
      TenantLoginMethodSettingRecord(
        id = UUID.randomUUID(),
        tenantId = tenantId,
        loginMethodId = methodId,
        isEnabled = true,
        allowSignup = true,
        displayOrder = 0,
        config = JsonObject(emptyMap()),
        secretRef = "secret-ref",
        createdBy = null,
        updatedBy = null,
        createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
      )

    "beginAuthorize returns authorization url for oidc" {
      coEvery { tenants.findByApiId(tenant.apiId.value) } returns tenant
      coEvery { loginMethods.findLoginMethodByApiId(oidcMethod.apiId.value) } returns oidcMethod
      coEvery { tenantLoginSettings.findTenantSetting(tenantId, methodId) } returns setting
      coEvery { secretGenerator.generate() } returnsMany listOf("state-secret", "verifier-secret")
      coEvery { oauthClient.pkceChallenge("verifier-secret") } returns "challenge"
      coEvery { credentialHasher.hash("state-secret") } returns "state-hash"
      coEvery {
        oauthClient.buildAuthorizeUrl(
          JsonObject(emptyMap()),
          "https://app.example.test/callback",
          "state-secret",
          "challenge",
          LoginMethodKind.OIDC,
        )
      } returns "https://idp.example.test/authorize"

      val result = runBlocking {
        service.beginAuthorize(
          loginMethodId = oidcMethod.apiId.value,
          tenantId = tenant.apiId.value,
          returnUrl = "/",
          redirectUri = "https://app.example.test/callback",
        )
      }

      result.authorizationUrl shouldBe "https://idp.example.test/authorize"
      result.state shouldBe "state-secret"
    }

    "beginAuthorize rejects unsupported login method kind" {
      val passwordMethod =
        oidcMethod.copy(kind = LoginMethodKind.PASSWORD, apiId = PublicId.new("lmd"))
      coEvery { tenants.findByApiId(tenant.apiId.value) } returns tenant
      coEvery { loginMethods.findLoginMethodByApiId(passwordMethod.apiId.value) } returns
        passwordMethod

      shouldThrow<InvalidRequestException> {
          runBlocking {
            service.beginAuthorize(
              loginMethodId = passwordMethod.apiId.value,
              tenantId = tenant.apiId.value,
              returnUrl = "/",
              redirectUri = "https://app.example.test/callback",
            )
          }
        }
        .errorCode shouldBe WorkbenchErrorCode.IDENTITY_FEDERATED_PROTOCOL_UNSUPPORTED
    }

    "beginAuthorize rejects disabled tenant setting" {
      coEvery { tenants.findByApiId(tenant.apiId.value) } returns tenant
      coEvery { loginMethods.findLoginMethodByApiId(oidcMethod.apiId.value) } returns oidcMethod
      coEvery { tenantLoginSettings.findTenantSetting(tenantId, methodId) } returns
        setting.copy(isEnabled = false)

      shouldThrow<InvalidRequestException> {
          runBlocking {
            service.beginAuthorize(
              loginMethodId = oidcMethod.apiId.value,
              tenantId = tenant.apiId.value,
              returnUrl = "/",
              redirectUri = "https://app.example.test/callback",
            )
          }
        }
        .errorCode shouldBe WorkbenchErrorCode.IDENTITY_LOGIN_METHOD_DISABLED
    }
  })
