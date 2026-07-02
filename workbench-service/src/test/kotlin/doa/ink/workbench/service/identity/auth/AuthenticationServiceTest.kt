package doa.ink.workbench.service.identity.auth

import doa.ink.workbench.core.common.errors.AuthenticationFailedException
import doa.ink.workbench.core.common.ids.PublicId
import doa.ink.workbench.core.identity.AuthEventRepository
import doa.ink.workbench.core.identity.LoginAccountRepository
import doa.ink.workbench.core.identity.TenantMemberRepository
import doa.ink.workbench.core.identity.UserRepository
import doa.ink.workbench.core.identity.auth.AuthSessionRepository
import doa.ink.workbench.core.identity.auth.BearerTokenRepository
import doa.ink.workbench.core.identity.auth.CredentialHasher
import doa.ink.workbench.core.identity.auth.CredentialSecretGenerator
import doa.ink.workbench.core.identity.auth.PasswordVerifier
import doa.ink.workbench.core.identity.model.AuditEventResult
import doa.ink.workbench.core.identity.model.AuthEventRecord
import doa.ink.workbench.core.identity.model.AuthEventType
import doa.ink.workbench.core.identity.model.AuthSessionRecord
import doa.ink.workbench.core.identity.model.CreateAuthEventCommand
import doa.ink.workbench.core.identity.model.CreateAuthSessionCommand
import doa.ink.workbench.core.identity.model.LoginAccountParameterKey
import doa.ink.workbench.core.identity.model.LoginAccountParameterRecord
import doa.ink.workbench.core.identity.model.LoginAccountRecord
import doa.ink.workbench.core.identity.model.LoginMethodKind
import doa.ink.workbench.core.identity.model.PasswordLoginCommand
import doa.ink.workbench.core.identity.model.TenantLoginMethodSettingRecord
import doa.ink.workbench.core.identity.model.TenantMemberRecord
import doa.ink.workbench.core.identity.model.TenantMemberStatus
import doa.ink.workbench.core.identity.model.UserRecord
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.JsonObject

class AuthenticationServiceTest :
  StringSpec({
    "password login issues a session and records success" {
      val fixture = Fixture()
      val result = fixture.service.loginWithPassword(fixture.loginCommand)

      result.session.secret shouldBe "secret-1"
      result.principal.user.id shouldBe fixture.user.id
      result.principal.loginAccountId shouldBe fixture.loginAccount.id
      fixture.appendedEvents.single().eventType shouldBe AuthEventType.LOGIN_SUCCESS
      coVerify(exactly = 1) { fixture.sessions.create(any()) }
      coVerify(exactly = 1) { fixture.loginAccounts.touchLastUsed(fixture.loginAccount.id, any()) }
    }

    "password login failure records a generic failure event" {
      val fixture = Fixture(passwordMatches = false)

      shouldThrow<AuthenticationFailedException> { fixture.service.loginWithPassword(fixture.loginCommand) }

      fixture.appendedEvents.single().eventType shouldBe AuthEventType.LOGIN_FAILURE
      fixture.appendedEvents.single().result shouldBe AuditEventResult.FAILURE
      fixture.appendedEvents.single().failureReason shouldBe "invalid_credentials"
      coVerify(exactly = 0) { fixture.sessions.create(any()) }
    }
  })

private class Fixture(passwordMatches: Boolean = true) {
  val tenantId: UUID = UUID.randomUUID()
  val user =
    UserRecord(
      id = UUID.randomUUID(),
      apiId = PublicId.new("usr"),
      displayName = "Ada",
      primaryEmail = "ada@example.test",
    )
  val loginAccount =
    LoginAccountRecord(
      id = UUID.randomUUID(),
      apiId = PublicId.new("lac"),
      loginMethodId = UUID.randomUUID(),
      subject = "Ada@Example.Test",
      normalizedSubject = "ada@example.test",
      displayName = "Ada",
      lastUsedAt = null,
      disabledAt = null,
      disabledBy = null,
      createdAt = now,
      updatedAt = now,
    )
  val loginCommand =
    PasswordLoginCommand(
      tenantId = tenantId,
      subject = "Ada@Example.Test",
      password = "correct-password",
      ipAddress = "127.0.0.1",
      userAgent = "test",
    )

  val users = mockk<UserRepository>()
  val tenantMembers = mockk<TenantMemberRepository>()
  val loginAccounts = mockk<LoginAccountRepository>()
  val authEvents = mockk<AuthEventRepository>()
  val sessions = mockk<AuthSessionRepository>()
  val bearerTokens = mockk<BearerTokenRepository>()
  val passwordVerifier = mockk<PasswordVerifier>()
  val appendedEvents = mutableListOf<CreateAuthEventCommand>()
  val secretGenerator =
    object : CredentialSecretGenerator {
      override fun generate(): String = "secret-1"
    }
  val hasher =
    object : CredentialHasher {
      override fun hash(secret: String): String = "hash:$secret"
    }
  val service =
    AuthenticationService(
      users = users,
      tenantMembers = tenantMembers,
      loginAccounts = loginAccounts,
      authEvents = authEvents,
      sessions = sessions,
      bearerTokens = bearerTokens,
      passwordVerifier = passwordVerifier,
      secretGenerator = secretGenerator,
      credentialHasher = hasher,
      clock = Clock.fixed(Instant.parse("2026-07-02T00:00:00Z"), ZoneOffset.UTC),
    )

  init {
    coEvery { loginAccounts.findLoginAccountByMethodAndSubject("password", "ada@example.test") } returns
      loginAccount
    coEvery { loginAccounts.findTenantSetting(tenantId, loginAccount.loginMethodId) } returns
      TenantLoginMethodSettingRecord(
        id = UUID.randomUUID(),
        tenantId = tenantId,
        loginMethodId = loginAccount.loginMethodId,
        isEnabled = true,
        allowSignup = false,
        displayOrder = 100,
        config = JsonObject(emptyMap()),
        secretRef = null,
        createdBy = null,
        updatedBy = null,
        createdAt = now,
        updatedAt = now,
      )
    coEvery { loginAccounts.findParameter(loginAccount.id, LoginAccountParameterKey.PasswordHash) } returns
      LoginAccountParameterRecord(
        id = UUID.randomUUID(),
        loginAccountId = loginAccount.id,
        parameterKey = LoginAccountParameterKey.PasswordHash,
        parameterValue = "bcrypt-hash",
        secretRef = null,
        metadata = JsonObject(emptyMap()),
        createdAt = now,
        updatedAt = now,
      )
    every { passwordVerifier.verify("correct-password", "bcrypt-hash") } returns passwordMatches
    coEvery { loginAccounts.findLinkedUser(loginAccount.id) } returns user
    coEvery { tenantMembers.findByTenantAndUser(tenantId, user.id) } returns
      TenantMemberRecord(
        id = UUID.randomUUID(),
        apiId = PublicId.new("tmb"),
        tenantId = tenantId,
        userId = user.id,
        status = TenantMemberStatus.ACTIVE,
        joinedAt = now,
        invitedBy = null,
        createdAt = now,
        updatedAt = now,
      )
    coEvery { sessions.create(any<CreateAuthSessionCommand>()) } answers {
      firstArg<CreateAuthSessionCommand>().let {
        AuthSessionRecord(
          id = UUID.randomUUID(),
          sessionHash = it.sessionHash,
          userId = it.userId,
          loginAccountId = it.loginAccountId,
          expiresAt = it.expiresAt,
          revokedAt = null,
          lastUsedAt = null,
          createdAt = now,
          updatedAt = now,
        )
      }
    }
    coEvery { loginAccounts.touchLastUsed(loginAccount.id, any()) } returns true
    coEvery { authEvents.append(any<CreateAuthEventCommand>()) } answers {
      firstArg<CreateAuthEventCommand>().let {
        appendedEvents += it
        AuthEventRecord(
          id = UUID.randomUUID(),
          authEventId = PublicId.new("aut"),
          tenantId = it.tenantId,
          userId = it.userId,
          loginAccountId = it.loginAccountId,
          loginMethodId = it.loginMethodId,
          eventType = it.eventType,
          result = it.result,
          failureReason = it.failureReason,
          ipAddress = it.ipAddress,
          userAgent = it.userAgent,
          metadata = it.metadata,
          occurredAt = now,
        )
      }
    }
  }

  companion object {
    val now: OffsetDateTime = OffsetDateTime.parse("2026-07-02T00:00:00Z")
  }
}
