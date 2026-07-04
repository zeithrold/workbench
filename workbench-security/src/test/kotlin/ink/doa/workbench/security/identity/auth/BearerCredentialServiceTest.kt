package ink.doa.workbench.security.identity.auth

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.AuthEventRepository
import ink.doa.workbench.core.identity.LoginAccountStore
import ink.doa.workbench.core.identity.UserRepository
import ink.doa.workbench.core.identity.auth.BearerTokenRepository
import ink.doa.workbench.core.identity.auth.CredentialHasher
import ink.doa.workbench.core.identity.auth.CredentialSecretGenerator
import ink.doa.workbench.core.identity.model.AuthEventType
import ink.doa.workbench.core.identity.model.BearerTokenRecord
import ink.doa.workbench.core.identity.model.CredentialType
import ink.doa.workbench.core.identity.model.UserRecord
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking

class BearerCredentialServiceTest :
  StringSpec({
    val users = mockk<UserRepository>()
    val loginAccounts = mockk<LoginAccountStore>(relaxed = true)
    val authEvents = mockk<AuthEventRepository>(relaxed = true)
    val bearerTokens = mockk<BearerTokenRepository>()
    val secretGenerator = mockk<CredentialSecretGenerator>()
    val credentialHasher = mockk<CredentialHasher>()
    val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
    val service =
      BearerCredentialService(
        users,
        loginAccounts,
        authEvents,
        bearerTokens,
        secretGenerator,
        credentialHasher,
        clock,
      )

    "createBearerToken issues token and records auth event" {
      val user = sampleUser()
      val loginAccountId = UUID.randomUUID()
      val tokenRecord = sampleBearerToken(user.id, loginAccountId)
      coEvery { users.findById(user.id) } returns user
      coEvery { secretGenerator.generate() } returns "secret-token"
      coEvery { credentialHasher.hash("secret-token") } returns "hashed-secret"
      coEvery { bearerTokens.create(any()) } returns tokenRecord

      val result = runBlocking {
        service.createBearerToken(
          userId = user.id,
          loginAccountId = loginAccountId,
          tenantId = null,
          name = "api",
          scopes = emptySet(),
          ipAddress = "127.0.0.1",
          userAgent = "test",
        )
      }

      result.secret shouldBe "secret-token"
      coVerify { authEvents.append(match { it.eventType == AuthEventType.TOKEN_CREATED }) }
    }

    "revokeBearerToken returns false when token is missing" {
      coEvery { credentialHasher.hash("missing") } returns "hashed-missing"
      coEvery {
        bearerTokens.findActiveByHash(
          "hashed-missing",
          OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        )
      } returns null

      runBlocking {
        service.revokeBearerToken("missing", ipAddress = null, userAgent = null)
      } shouldBe false
    }

    "authenticateBearerToken returns principal for active token" {
      val user = sampleUser()
      val token = sampleBearerToken(user.id, UUID.randomUUID())
      coEvery { credentialHasher.hash("valid-token") } returns token.tokenHash
      coEvery {
        bearerTokens.findActiveByHash(token.tokenHash, OffsetDateTime.parse("2026-07-04T00:00:00Z"))
      } returns token
      coEvery { users.findById(user.id) } returns user
      coEvery { bearerTokens.touch(token.id, any()) } returns true
      coEvery { loginAccounts.touchLastUsed(token.loginAccountId, any()) } returns true

      val principal = runBlocking { service.authenticateBearerToken("valid-token") }

      principal?.user?.id shouldBe user.id
      principal?.credentialType shouldBe CredentialType.BEARER_TOKEN
    }

    "authenticateBearerToken returns null when user is missing" {
      val token = sampleBearerToken(UUID.randomUUID(), UUID.randomUUID())
      coEvery { credentialHasher.hash("orphan-token") } returns token.tokenHash
      coEvery {
        bearerTokens.findActiveByHash(token.tokenHash, OffsetDateTime.parse("2026-07-04T00:00:00Z"))
      } returns token
      coEvery { users.findById(token.userId) } returns null

      runBlocking { service.authenticateBearerToken("orphan-token") }.shouldBeNull()
    }
  })

private fun sampleUser(): UserRecord =
  UserRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("usr"),
    displayName = "Ada",
    primaryEmail = "ada@example.test",
  )

private fun sampleBearerToken(userId: UUID, loginAccountId: UUID): BearerTokenRecord {
  val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")
  return BearerTokenRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("btk"),
    tokenHash = "hashed-secret",
    userId = userId,
    loginAccountId = loginAccountId,
    tenantId = null,
    name = "api",
    scopes = setOf("workbench.api"),
    createdBy = userId,
    expiresAt = now.plusDays(30),
    revokedAt = null,
    lastUsedAt = null,
    createdAt = now,
    updatedAt = now,
  )
}
