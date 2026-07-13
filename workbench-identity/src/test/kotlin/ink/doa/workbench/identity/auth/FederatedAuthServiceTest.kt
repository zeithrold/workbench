package ink.doa.workbench.identity.auth

import ink.doa.workbench.identity.LoginAccountStore
import ink.doa.workbench.identity.LoginMethodRepository
import ink.doa.workbench.identity.TenantLoginMethodSettingRepository
import ink.doa.workbench.identity.UserLoginAccountRepository
import ink.doa.workbench.identity.model.AuthLoginStateRecord
import ink.doa.workbench.identity.model.LoginAccountRecord
import ink.doa.workbench.identity.model.LoginMethodDefinitionRecord
import ink.doa.workbench.identity.model.LoginMethodKind
import ink.doa.workbench.identity.model.TenantLoginMethodSettingRecord
import ink.doa.workbench.identity.model.UserRecord
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import ink.doa.workbench.kernel.common.ids.PublicId
import ink.doa.workbench.tenant.TenantRepository
import ink.doa.workbench.tenant.model.TenantRecord
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
    val repositories =
      FederatedAuthRepositories(
        loginMethods,
        tenantLoginSettings,
        loginAccounts,
        userLoginAccounts,
        tenants,
        loginStates,
      )
    val clients = FederatedAuthClients(oauthClient, samlClient)
    val crypto = CredentialCryptoSupport(secretGenerator, credentialHasher)
    val service = FederatedAuthService(repositories, clients, crypto, clock)

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

    "beginAuthorize returns authorization url for saml" {
      val samlMethod =
        oidcMethod.copy(
          code = "saml",
          kind = LoginMethodKind.SAML,
          apiId = PublicId.new("lmd"),
        )
      coEvery { tenants.findByApiId(tenant.apiId.value) } returns tenant
      coEvery { loginMethods.findLoginMethodByApiId(samlMethod.apiId.value) } returns samlMethod
      coEvery { tenantLoginSettings.findTenantSetting(tenantId, methodId) } returns setting
      coEvery { secretGenerator.generate() } returnsMany listOf("state-secret", "verifier-secret")
      coEvery { oauthClient.pkceChallenge("verifier-secret") } returns "challenge"
      coEvery { credentialHasher.hash("state-secret") } returns "state-hash"
      coEvery {
        samlClient.buildAuthorizeUrl(
          JsonObject(emptyMap()),
          "https://app.example.test/callback",
          "state-secret",
        )
      } returns "https://idp.example.test/saml/sso"

      val result = runBlocking {
        service.beginAuthorize(
          loginMethodId = samlMethod.apiId.value,
          tenantId = tenant.apiId.value,
          returnUrl = "/",
          redirectUri = "https://app.example.test/callback",
        )
      }

      result.authorizationUrl shouldBe "https://idp.example.test/saml/sso"
      result.state shouldBe "state-secret"
    }

    "completeOAuthCallback resolves federated identity" {
      val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")
      val loginStateId = UUID.randomUUID()
      val loginState =
        AuthLoginStateRecord(
          id = loginStateId,
          stateHash = "state-hash",
          tenantId = tenantId,
          loginMethodId = methodId,
          redirectUri = "https://app.example.test/callback",
          pkceVerifier = "verifier-secret",
          returnUrl = "/",
          expiresAt = now.plusMinutes(10),
          consumedAt = null,
          createdAt = now,
        )
      val user =
        UserRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("usr"),
          displayName = "Ada",
          primaryEmail = "ada@example.test",
        )
      val account =
        LoginAccountRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("lac"),
          loginMethodId = methodId,
          subject = "ada@example.test",
          normalizedSubject = "ada@example.test",
          displayName = "Ada",
          lastUsedAt = null,
          disabledAt = null,
          disabledBy = null,
          createdAt = now,
          updatedAt = now,
        )
      val tokenResponse = JsonObject(mapOf("id_token" to JsonPrimitive("header.payload.signature")))
      coEvery { credentialHasher.hash("oauth-state") } returns "state-hash"
      coEvery { loginStates.findActiveByStateHash("state-hash", now) } returns loginState
      coEvery { loginStates.consume(loginStateId, now) } returns true
      coEvery { loginMethods.findLoginMethodById(methodId) } returns oidcMethod
      coEvery { tenantLoginSettings.findTenantSetting(tenantId, methodId) } returns setting
      coEvery {
        oauthClient.exchangeAuthorizationCode(
          JsonObject(emptyMap()),
          setting.secretRef,
          "auth-code",
          "https://app.example.test/callback",
          "verifier-secret",
        )
      } returns tokenResponse
      coEvery {
        oauthClient.resolveSubject(JsonObject(emptyMap()), tokenResponse, LoginMethodKind.OIDC)
      } returns "ada@example.test"
      coEvery {
        loginAccounts.findLoginAccountByMethodAndSubject("oidc", "ada@example.test")
      } returns account
      coEvery { userLoginAccounts.findLinkedUser(account.id) } returns user

      val result = runBlocking {
        service.completeOAuthCallback(
          code = "auth-code",
          state = "oauth-state",
          redirectUri = "https://app.example.test/callback",
        )
      }

      result.identity.user.id shouldBe user.id
      result.tenantId shouldBe tenantId
    }

    "completeSamlAcs resolves federated identity from relay state" {
      val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")
      val loginStateId = UUID.randomUUID()
      val samlMethod =
        oidcMethod.copy(
          code = "saml",
          kind = LoginMethodKind.SAML,
          apiId = PublicId.new("lmd"),
        )
      val loginState =
        AuthLoginStateRecord(
          id = loginStateId,
          stateHash = "relay-hash",
          tenantId = tenantId,
          loginMethodId = methodId,
          redirectUri = "https://app.example.test/acs",
          pkceVerifier = null,
          returnUrl = "/",
          expiresAt = now.plusMinutes(10),
          consumedAt = null,
          createdAt = now,
        )
      val user =
        UserRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("usr"),
          displayName = "Ada",
          primaryEmail = "ada@example.test",
        )
      val account =
        LoginAccountRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("lac"),
          loginMethodId = methodId,
          subject = "ada@example.test",
          normalizedSubject = "ada@example.test",
          displayName = "Ada",
          lastUsedAt = null,
          disabledAt = null,
          disabledBy = null,
          createdAt = now,
          updatedAt = now,
        )
      val xml = """<Response><NameID>ada@example.test</NameID></Response>"""
      val samlResponse = Base64.getEncoder().encodeToString(xml.toByteArray(StandardCharsets.UTF_8))
      coEvery { credentialHasher.hash("relay-state") } returns "relay-hash"
      coEvery { loginStates.findActiveByStateHash("relay-hash", now) } returns loginState
      coEvery { loginStates.consume(loginStateId, now) } returns true
      coEvery { loginMethods.findLoginMethodById(methodId) } returns samlMethod
      every { samlClient.parseNameId(samlResponse) } returns "ada@example.test"
      coEvery {
        loginAccounts.findLoginAccountByMethodAndSubject("saml", "ada@example.test")
      } returns account
      coEvery { userLoginAccounts.findLinkedUser(account.id) } returns user

      val result = runBlocking { service.completeSamlAcs(samlResponse, "relay-state") }

      result.identity.user.id shouldBe user.id
      result.tenantId shouldBe tenantId
    }

    "completeSamlAcs rejects invalid relay state" {
      val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")
      coEvery { credentialHasher.hash("invalid-relay") } returns "missing-hash"
      coEvery { loginStates.findActiveByStateHash("missing-hash", now) } returns null

      shouldThrow<InvalidRequestException> {
          runBlocking { service.completeSamlAcs("response", "invalid-relay") }
        }
        .errorCode shouldBe WorkbenchErrorCode.IDENTITY_FEDERATED_SAML_RELAY_STATE_INVALID
    }
  })
