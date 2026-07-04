package ink.doa.workbench.security.identity.auth

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.identity.LoginAccountStore
import ink.doa.workbench.core.identity.LoginMethodRepository
import ink.doa.workbench.core.identity.TenantLoginMethodSettingRepository
import ink.doa.workbench.core.identity.TenantRepository
import ink.doa.workbench.core.identity.UserLoginAccountRepository
import ink.doa.workbench.core.identity.auth.CredentialHasher
import ink.doa.workbench.core.identity.auth.CredentialSecretGenerator
import ink.doa.workbench.core.identity.auth.MagicLinkTokenRepository
import ink.doa.workbench.tenant.tenantconfig.TenantConfigService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking

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
    val service =
      MagicLinkAuthService(
        loginMethods,
        tenantLoginSettings,
        loginAccounts,
        userLoginAccounts,
        tenants,
        magicLinkTokens,
        credentialHasher,
        secretGenerator,
        tenantConfig,
        clock,
      )

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
        ink.doa.workbench.core.identity.model.UserRecord(
          id = java.util.UUID.randomUUID(),
          apiId = ink.doa.workbench.core.common.ids.PublicId.new("usr"),
          displayName = "Ada",
          primaryEmail = "ada@example.test",
        )
      val account =
        ink.doa.workbench.core.identity.model.LoginAccountRecord(
          id = accountId,
          apiId = ink.doa.workbench.core.common.ids.PublicId.new("lac"),
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
        ink.doa.workbench.core.identity.model.MagicLinkTokenRecord(
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
        ink.doa.workbench.core.identity.model.LoginMethodDefinitionRecord(
          id = loginMethodId,
          apiId = ink.doa.workbench.core.common.ids.PublicId.new("lmd"),
          code = "magic_link",
          kind = ink.doa.workbench.core.identity.model.LoginMethodKind.EMAIL_MAGIC_LINK,
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
        ink.doa.workbench.core.identity.model.MagicLinkTokenRecord(
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
        ink.doa.workbench.core.identity.model.MagicLinkTokenRecord(
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
        ink.doa.workbench.core.identity.model.LoginMethodDefinitionRecord(
          id = loginMethodId,
          apiId = ink.doa.workbench.core.common.ids.PublicId.new("lmd"),
          code = "magic_link",
          kind = ink.doa.workbench.core.identity.model.LoginMethodKind.EMAIL_MAGIC_LINK,
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
  })
