package one.ztd.workbench.identity.auth

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
import one.ztd.workbench.identity.LoginAccountStore
import one.ztd.workbench.identity.LoginMethodRepository
import one.ztd.workbench.identity.TenantLoginMethodSettingRepository
import one.ztd.workbench.identity.UserLoginAccountRepository
import one.ztd.workbench.identity.model.LoginMethodDefinitionRecord
import one.ztd.workbench.identity.model.LoginMethodKind
import one.ztd.workbench.identity.model.TenantLoginMethodSettingRecord
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.tenant.TenantRepository
import one.ztd.workbench.tenant.model.TenantRecord
import one.ztd.workbench.tenant.tenantconfig.TenantConfigService
import one.ztd.workbench.tenant.tenantconfig.model.MailSmtpTenantConfig
import one.ztd.workbench.tenant.tenantconfig.model.TenantConfigSpecs

class MagicLinkAuthServiceTest :
  StringSpec({
    val loginMethods = mockk<LoginMethodRepository>(relaxed = true)
    val tenantLoginSettings = mockk<TenantLoginMethodSettingRepository>(relaxed = true)
    val loginAccounts = mockk<LoginAccountStore>(relaxed = true)
    val userLoginAccounts = mockk<UserLoginAccountRepository>(relaxed = true)
    val tenants = mockk<TenantRepository>(relaxed = true)
    val magicLinkTokens = mockk<MagicLinkTokenRepository>()
    val credentialHasher = mockk<CredentialHasher>()
    val secretGenerator = mockk<CredentialSecretGenerator>(relaxed = true)
    val tenantConfig = mockk<TenantConfigService>(relaxed = true)
    val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
    val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")
    val repositories =
      MagicLinkAuthRepositories(
        loginMethods,
        tenantLoginSettings,
        loginAccounts,
        userLoginAccounts,
        tenants,
        magicLinkTokens,
      )
    val crypto = CredentialCryptoSupport(secretGenerator, credentialHasher)
    val tokenIssuer = MagicLinkTokenIssuer(repositories, crypto, clock)
    val tokenVerifier = MagicLinkTokenVerifier(repositories, crypto, clock)
    val delivery = RecordingMagicLinkDeliveryPort()
    val service =
      MagicLinkAuthService(repositories, tokenIssuer, tokenVerifier, delivery, tenantConfig)

    beforeTest {
      delivery.sent.clear()
    }

    "resolveToken throws when token is invalid" {
      coEvery { credentialHasher.hash("invalid-token") } returns "hashed-invalid"
      coEvery { magicLinkTokens.findActiveByHash("hashed-invalid", now) } returns null

      shouldThrow<InvalidRequestException> {
        runBlocking { service.resolveToken("invalid-token") }
      }
    }

    "resolveToken returns identity for valid token" {
      val tenantId = java.util.UUID.randomUUID()
      val loginMethodId = java.util.UUID.randomUUID()
      val accountId = java.util.UUID.randomUUID()
      val user =
        one.ztd.workbench.identity.model.UserRecord(
          id = java.util.UUID.randomUUID(),
          apiId = one.ztd.workbench.kernel.common.ids.PublicId.new("usr"),
          displayName = "Ada",
          primaryEmail = "ada@example.test",
        )
      val account =
        one.ztd.workbench.identity.model.LoginAccountRecord(
          id = accountId,
          apiId = one.ztd.workbench.kernel.common.ids.PublicId.new("lac"),
          loginMethodId = loginMethodId,
          subject = "ada@example.test",
          normalizedSubject = "ada@example.test",
          displayName = "Ada",
          lastUsedAt = null,
          disabledAt = null,
          disabledBy = null,
          createdAt = now,
          updatedAt = now,
        )
      val tokenRecord =
        one.ztd.workbench.identity.model.MagicLinkTokenRecord(
          id = java.util.UUID.randomUUID(),
          tokenHash = "hashed-valid",
          loginMethodId = loginMethodId,
          tenantId = tenantId,
          normalizedSubject = "ada@example.test",
          expiresAt = now.plusMinutes(10),
          consumedAt = null,
          createdAt = now,
        )

      coEvery { credentialHasher.hash("valid-token") } returns "hashed-valid"
      coEvery { magicLinkTokens.findActiveByHash("hashed-valid", now) } returns tokenRecord
      coEvery { magicLinkTokens.consume(tokenRecord.id, now) } returns true
      coEvery { loginMethods.findLoginMethodById(loginMethodId) } returns
        one.ztd.workbench.identity.model.LoginMethodDefinitionRecord(
          id = loginMethodId,
          apiId = one.ztd.workbench.kernel.common.ids.PublicId.new("lmd"),
          code = "magic_link",
          kind = one.ztd.workbench.identity.model.LoginMethodKind.EMAIL_MAGIC_LINK,
          name = "Magic Link",
          isBuiltin = true,
          isEnabledGlobally = true,
          createdAt = now,
          updatedAt = now,
        )
      coEvery {
        loginAccounts.findLoginAccountByMethodAndSubject("magic_link", "ada@example.test")
      } returns account
      coEvery { userLoginAccounts.findLinkedUser(accountId) } returns user

      val identity = runBlocking { service.resolveToken("valid-token") }

      identity.user.id shouldBe user.id
      identity.tenantId shouldBe tenantId
    }

    "resolveToken throws when login method is missing" {
      val tokenRecord =
        one.ztd.workbench.identity.model.MagicLinkTokenRecord(
          id = java.util.UUID.randomUUID(),
          tokenHash = "hashed-valid",
          loginMethodId = java.util.UUID.randomUUID(),
          tenantId = java.util.UUID.randomUUID(),
          normalizedSubject = "ada@example.test",
          expiresAt = now.plusMinutes(10),
          consumedAt = null,
          createdAt = now,
        )
      coEvery { credentialHasher.hash("valid-token") } returns "hashed-valid"
      coEvery { magicLinkTokens.findActiveByHash("hashed-valid", now) } returns tokenRecord
      coEvery { magicLinkTokens.consume(tokenRecord.id, now) } returns true
      coEvery { loginMethods.findLoginMethodById(tokenRecord.loginMethodId) } returns null

      shouldThrow<InvalidRequestException> {
        runBlocking { service.resolveToken("valid-token") }
      }
    }

    "resolveToken throws when login account is missing" {
      val loginMethodId = java.util.UUID.randomUUID()
      val tokenRecord =
        one.ztd.workbench.identity.model.MagicLinkTokenRecord(
          id = java.util.UUID.randomUUID(),
          tokenHash = "hashed-valid",
          loginMethodId = loginMethodId,
          tenantId = java.util.UUID.randomUUID(),
          normalizedSubject = "ada@example.test",
          expiresAt = now.plusMinutes(10),
          consumedAt = null,
          createdAt = now,
        )
      coEvery { credentialHasher.hash("valid-token") } returns "hashed-valid"
      coEvery { magicLinkTokens.findActiveByHash("hashed-valid", now) } returns tokenRecord
      coEvery { magicLinkTokens.consume(tokenRecord.id, now) } returns true
      coEvery { loginMethods.findLoginMethodById(loginMethodId) } returns
        one.ztd.workbench.identity.model.LoginMethodDefinitionRecord(
          id = loginMethodId,
          apiId = one.ztd.workbench.kernel.common.ids.PublicId.new("lmd"),
          code = "magic_link",
          kind = one.ztd.workbench.identity.model.LoginMethodKind.EMAIL_MAGIC_LINK,
          name = "Magic Link",
          isBuiltin = true,
          isEnabledGlobally = true,
          createdAt = now,
          updatedAt = now,
        )
      coEvery {
        loginAccounts.findLoginAccountByMethodAndSubject("magic_link", "ada@example.test")
      } returns null

      shouldThrow<InvalidRequestException> {
        runBlocking { service.resolveToken("valid-token") }
      }
    }

    "resolveToken throws when linked user is missing" {
      val loginMethodId = UUID.randomUUID()
      val accountId = UUID.randomUUID()
      val account =
        one.ztd.workbench.identity.model.LoginAccountRecord(
          id = accountId,
          apiId = PublicId.new("lac"),
          loginMethodId = loginMethodId,
          subject = "ada@example.test",
          normalizedSubject = "ada@example.test",
          displayName = "Ada",
          lastUsedAt = null,
          disabledAt = null,
          disabledBy = null,
          createdAt = now,
          updatedAt = now,
        )
      val tokenRecord =
        one.ztd.workbench.identity.model.MagicLinkTokenRecord(
          id = UUID.randomUUID(),
          tokenHash = "hashed-valid",
          loginMethodId = loginMethodId,
          tenantId = UUID.randomUUID(),
          normalizedSubject = "ada@example.test",
          expiresAt = now.plusMinutes(10),
          consumedAt = null,
          createdAt = now,
        )
      coEvery { credentialHasher.hash("valid-token") } returns "hashed-valid"
      coEvery { magicLinkTokens.findActiveByHash("hashed-valid", now) } returns tokenRecord
      coEvery { magicLinkTokens.consume(tokenRecord.id, now) } returns true
      coEvery { loginMethods.findLoginMethodById(loginMethodId) } returns
        LoginMethodDefinitionRecord(
          id = loginMethodId,
          apiId = PublicId.new("lmd"),
          code = "magic_link",
          kind = LoginMethodKind.EMAIL_MAGIC_LINK,
          name = "Magic Link",
          isBuiltin = true,
          isEnabledGlobally = true,
          createdAt = now,
          updatedAt = now,
        )
      coEvery {
        loginAccounts.findLoginAccountByMethodAndSubject("magic_link", "ada@example.test")
      } returns account
      coEvery { userLoginAccounts.findLinkedUser(accountId) } returns null

      shouldThrow<InvalidRequestException> {
        runBlocking { service.resolveToken("valid-token") }
      }
    }

    "requestMagicLink rejects non magic link methods" {
      val tenant =
        TenantRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("ten"),
          slug = "acme",
          name = "Acme",
        )
      val method =
        LoginMethodDefinitionRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("lmd"),
          code = "password",
          kind = LoginMethodKind.PASSWORD,
          name = "Password",
          isBuiltin = true,
          isEnabledGlobally = true,
          createdAt = now,
          updatedAt = now,
        )
      coEvery { tenants.findByApiId(tenant.apiId.value) } returns tenant
      coEvery { loginMethods.findLoginMethodByApiId(method.apiId.value) } returns method

      shouldThrow<InvalidRequestException> {
          runBlocking {
            service.requestMagicLink("ada@example.test", tenant.apiId.value, method.apiId.value)
          }
        }
        .errorCode shouldBe WorkbenchErrorCode.IDENTITY_LOGIN_METHOD_NOT_MAGIC_LINK
    }

    "requestMagicLink rejects disabled tenant setting" {
      val tenantId = UUID.randomUUID()
      val methodId = UUID.randomUUID()
      val tenant =
        TenantRecord(
          id = tenantId,
          apiId = PublicId.new("ten"),
          slug = "acme",
          name = "Acme",
        )
      val method =
        LoginMethodDefinitionRecord(
          id = methodId,
          apiId = PublicId.new("lmd"),
          code = "magic_link",
          kind = LoginMethodKind.EMAIL_MAGIC_LINK,
          name = "Magic Link",
          isBuiltin = true,
          isEnabledGlobally = true,
          createdAt = now,
          updatedAt = now,
        )
      coEvery { tenants.findByApiId(tenant.apiId.value) } returns tenant
      coEvery { loginMethods.findLoginMethodByApiId(method.apiId.value) } returns method
      coEvery { tenantLoginSettings.findTenantSetting(tenantId, methodId) } returns
        TenantLoginMethodSettingRecord(
          id = UUID.randomUUID(),
          tenantId = tenantId,
          loginMethodId = methodId,
          isEnabled = false,
          allowSignup = true,
          displayOrder = 0,
          config = JsonObject(emptyMap()),
          secretRef = null,
          createdBy = null,
          updatedBy = null,
          createdAt = now,
          updatedAt = now,
        )

      shouldThrow<InvalidRequestException> {
          runBlocking {
            service.requestMagicLink("ada@example.test", tenant.apiId.value, method.apiId.value)
          }
        }
        .errorCode shouldBe WorkbenchErrorCode.IDENTITY_MAGIC_LINK_DISABLED
    }

    "requestMagicLink creates token and submits delivery command" {
      val tenantId = UUID.randomUUID()
      val methodId = UUID.randomUUID()
      val tenant =
        TenantRecord(
          id = tenantId,
          apiId = PublicId.new("ten"),
          slug = "acme",
          name = "Acme",
        )
      val method =
        LoginMethodDefinitionRecord(
          id = methodId,
          apiId = PublicId.new("lmd"),
          code = "magic_link",
          kind = LoginMethodKind.EMAIL_MAGIC_LINK,
          name = "Magic Link",
          isBuiltin = true,
          isEnabledGlobally = true,
          createdAt = now,
          updatedAt = now,
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
          secretRef = null,
          createdBy = null,
          updatedBy = null,
          createdAt = now,
          updatedAt = now,
        )
      coEvery { tenants.findByApiId(tenant.apiId.value) } returns tenant
      coEvery { loginMethods.findLoginMethodByApiId(method.apiId.value) } returns method
      coEvery { tenantLoginSettings.findTenantSetting(tenantId, methodId) } returns setting
      coEvery { tenantConfig.get(tenantId, TenantConfigSpecs.MailSmtp) } returns
        MailSmtpTenantConfig(
          enabled = true,
          fromAddress = "noreply@acme.test",
          host = "127.0.0.1",
          port = 1,
          username = null,
          passwordSecretRef = null,
        )
      coEvery { secretGenerator.generate() } returns "magic-secret"
      coEvery { credentialHasher.hash("magic-secret") } returns "magic-hash"
      coEvery {
        magicLinkTokens.create(
          tokenHash = "magic-hash",
          loginMethodId = methodId,
          tenantId = tenantId,
          normalizedSubject = "ada@example.test",
          expiresAt = now.plusMinutes(15),
        )
      } returns
        one.ztd.workbench.identity.model.MagicLinkTokenRecord(
          id = UUID.randomUUID(),
          tokenHash = "magic-hash",
          loginMethodId = methodId,
          tenantId = tenantId,
          normalizedSubject = "ada@example.test",
          expiresAt = now.plusMinutes(15),
          consumedAt = null,
          createdAt = now,
        )

      runBlocking {
        service.requestMagicLink("Ada@Example.Test", tenant.apiId.value, method.apiId.value)
      }

      delivery.sent.single() shouldBe
        SendMagicLinkCommand(
          to = "ada@example.test",
          token = "magic-secret",
          mailConfig =
            MailSmtpTenantConfig(
              enabled = true,
              fromAddress = "noreply@acme.test",
              host = "127.0.0.1",
              port = 1,
              username = null,
              passwordSecretRef = null,
            ),
        )
    }
  })

private class RecordingMagicLinkDeliveryPort : MagicLinkDeliveryPort {
  val sent = mutableListOf<SendMagicLinkCommand>()

  override suspend fun send(command: SendMagicLinkCommand) {
    sent += command
  }
}
