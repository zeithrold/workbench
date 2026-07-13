package ink.doa.workbench.identity.auth

import ink.doa.workbench.identity.AuthEventRepository
import ink.doa.workbench.identity.LoginAccountStore
import ink.doa.workbench.identity.UserRepository
import ink.doa.workbench.identity.model.AuthSessionRecord
import ink.doa.workbench.identity.model.CredentialType
import ink.doa.workbench.identity.model.UserRecord
import ink.doa.workbench.kernel.common.ids.PublicId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
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

class SessionCredentialServiceTest :
  StringSpec({
    val users = mockk<UserRepository>()
    val loginAccounts = mockk<LoginAccountStore>(relaxed = true)
    val authEvents = mockk<AuthEventRepository>(relaxed = true)
    val sessions = mockk<AuthSessionRepository>()
    val secretGenerator = mockk<CredentialSecretGenerator>()
    val credentialHasher = mockk<CredentialHasher>()
    val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
    val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")
    val credentials = AuthCredentialSupport(users, loginAccounts, authEvents)
    val crypto = CredentialCryptoSupport(secretGenerator, credentialHasher)
    val service = SessionCredentialService(credentials, crypto, sessions, clock)

    "issueSession returns issued credential" {
      val user = sampleUser()
      val loginAccountId = UUID.randomUUID()
      val session = sampleSession(user.id, loginAccountId)
      coEvery { users.findById(user.id) } returns user
      coEvery { secretGenerator.generate() } returns "session-secret"
      coEvery { credentialHasher.hash("session-secret") } returns "hashed-session"
      coEvery { sessions.create(any()) } returns session

      val result = runBlocking {
        service.issueSession(user.id, loginAccountId, now, activeTenantId = null)
      }

      result.secret shouldBe "session-secret"
      result.id shouldBe session.id
      result.expiresAt shouldBe session.expiresAt
    }

    "logoutSession returns false when session is missing" {
      coEvery { credentialHasher.hash("missing-session") } returns "hashed-missing"
      coEvery { sessions.findActiveByHash("hashed-missing", now) } returns null

      runBlocking {
        service.logoutSession("missing-session", ipAddress = null, userAgent = null)
      } shouldBe false
    }

    "authenticateSession returns principal when session is valid" {
      val user = sampleUser()
      val session = sampleSession(user.id, UUID.randomUUID())
      coEvery { credentialHasher.hash("valid-session") } returns session.sessionHash
      coEvery { sessions.findActiveByHash(session.sessionHash, now) } returns session
      coEvery { users.findById(user.id) } returns user
      coEvery { sessions.touch(session.id, now) } returns true
      coEvery { loginAccounts.touchLastUsed(session.loginAccountId, now) } returns true

      val principal = runBlocking { service.authenticateSession("valid-session") }

      principal.shouldNotBeNull()
      principal.user.id shouldBe user.id
      principal.loginAccountId shouldBe session.loginAccountId
      principal.credentialType shouldBe CredentialType.SESSION
      coVerify { sessions.touch(session.id, now) }
    }
  })

private fun sampleUser(): UserRecord =
  UserRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("usr"),
    displayName = "Ada",
    primaryEmail = "ada@example.test",
  )

private fun sampleSession(userId: UUID, loginAccountId: UUID): AuthSessionRecord {
  val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")
  return AuthSessionRecord(
    id = UUID.randomUUID(),
    sessionHash = "hashed-session",
    userId = userId,
    loginAccountId = loginAccountId,
    activeTenantId = null,
    expiresAt = now.plusHours(12),
    revokedAt = null,
    lastUsedAt = null,
    createdAt = now,
    updatedAt = now,
  )
}
