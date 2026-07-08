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
import ink.doa.workbench.core.identity.model.MagicLinkTokenRecord
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking

class MagicLinkTokenServiceTest :
  StringSpec({
    val magicLinkTokens = mockk<MagicLinkTokenRepository>()
    val repositories =
      MagicLinkAuthRepositories(
        loginMethods = mockk<LoginMethodRepository>(),
        tenantLoginSettings = mockk<TenantLoginMethodSettingRepository>(),
        loginAccounts = mockk<LoginAccountStore>(),
        userLoginAccounts = mockk<UserLoginAccountRepository>(),
        tenants = mockk<TenantRepository>(),
        magicLinkTokens = magicLinkTokens,
      )
    val secretGenerator = mockk<CredentialSecretGenerator>()
    val credentialHasher = mockk<CredentialHasher>()
    val crypto = CredentialCryptoSupport(secretGenerator, credentialHasher)
    val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
    val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")
    val issuer = MagicLinkTokenIssuer(repositories, crypto, clock)
    val verifier = MagicLinkTokenVerifier(repositories, crypto, clock)

    "issue creates a hashed token with requested ttl and returns the secret" {
      val loginMethodId = UUID.randomUUID()
      val tenantId = UUID.randomUUID()
      val record =
        MagicLinkTokenRecord(
          id = UUID.randomUUID(),
          tokenHash = "hashed-secret",
          loginMethodId = loginMethodId,
          tenantId = tenantId,
          normalizedSubject = "ada@example.test",
          expiresAt = now.plusMinutes(15),
          consumedAt = null,
          createdAt = now,
        )
      every { secretGenerator.generate() } returns "magic-secret"
      every { credentialHasher.hash("magic-secret") } returns "hashed-secret"
      coEvery {
        magicLinkTokens.create(
          tokenHash = "hashed-secret",
          loginMethodId = loginMethodId,
          tenantId = tenantId,
          normalizedSubject = "ada@example.test",
          expiresAt = now.plusMinutes(15),
        )
      } returns record

      val issued = runBlocking {
        issuer.issue(
          loginMethodId = loginMethodId,
          tenantId = tenantId,
          normalizedSubject = "ada@example.test",
          ttl = Duration.ofMinutes(15),
        )
      }

      issued.secret shouldBe "magic-secret"
      issued.record shouldBe record
    }

    "verifyAndConsume returns active token and consumes it at current time" {
      val record =
        MagicLinkTokenRecord(
          id = UUID.randomUUID(),
          tokenHash = "hashed-token",
          loginMethodId = UUID.randomUUID(),
          tenantId = UUID.randomUUID(),
          normalizedSubject = "ada@example.test",
          expiresAt = now.plusMinutes(10),
          consumedAt = null,
          createdAt = now,
        )
      every { credentialHasher.hash("plain-token") } returns "hashed-token"
      coEvery { magicLinkTokens.findActiveByHash("hashed-token", now) } returns record
      coEvery { magicLinkTokens.consume(record.id, now) } returns true

      val verified = runBlocking { verifier.verifyAndConsume("plain-token") }

      verified shouldBe record
      coVerify(exactly = 1) { magicLinkTokens.consume(record.id, now) }
    }

    "verifyAndConsume rejects missing or expired tokens" {
      every { credentialHasher.hash("missing-token") } returns "hashed-missing"
      coEvery { magicLinkTokens.findActiveByHash("hashed-missing", now) } returns null

      shouldThrow<InvalidRequestException> {
        runBlocking { verifier.verifyAndConsume("missing-token") }
      }
    }
  })
