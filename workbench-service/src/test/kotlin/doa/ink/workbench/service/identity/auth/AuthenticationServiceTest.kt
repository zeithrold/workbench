package doa.ink.workbench.service.identity.auth

import doa.ink.workbench.core.common.errors.AuthenticationFailedException
import doa.ink.workbench.core.common.ids.PublicId
import doa.ink.workbench.core.identity.AuthEventRepository
import doa.ink.workbench.core.identity.LoginAccountStore
import doa.ink.workbench.core.identity.model.AuditEventResult
import doa.ink.workbench.core.identity.model.AuthEventRecord
import doa.ink.workbench.core.identity.model.AuthEventType
import doa.ink.workbench.core.identity.model.AuthenticatedIdentity
import doa.ink.workbench.core.identity.model.CreateAuthEventCommand
import doa.ink.workbench.core.identity.model.IssuedCredential
import doa.ink.workbench.core.identity.model.LoginAccountRecord
import doa.ink.workbench.core.identity.model.LoginCommand
import doa.ink.workbench.core.identity.model.LoginMethodKind
import doa.ink.workbench.core.identity.model.UserRecord
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
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

class AuthenticationServiceTest :
  StringSpec({
    "password login issues a session and records success" {
      val fixture = Fixture()
      val result = runBlocking { fixture.service.login(fixture.loginCommand) }

      result.session.secret shouldBe "secret-1"
      result.principal.user.id shouldBe fixture.user.id
      result.principal.loginAccountId shouldBe fixture.loginAccount.id
      fixture.appendedEvents.single().eventType shouldBe AuthEventType.LOGIN_SUCCESS
      coVerify(exactly = 1) {
        fixture.sessionCredentialService.issueSession(
          fixture.user.id,
          fixture.loginAccount.id,
          any(),
          any(),
        )
      }
      coVerify(exactly = 1) { fixture.loginAccounts.touchLastUsed(fixture.loginAccount.id, any()) }
    }

    "password login failure records a generic failure event" {
      val fixture = Fixture(authenticateFails = true)

      shouldThrow<AuthenticationFailedException> {
        runBlocking { fixture.service.login(fixture.loginCommand) }
      }

      fixture.appendedEvents.single().eventType shouldBe AuthEventType.LOGIN_FAILURE
      fixture.appendedEvents.single().result shouldBe AuditEventResult.FAILURE
      coVerify(exactly = 0) {
        fixture.sessionCredentialService.issueSession(any(), any(), any(), any())
      }
    }
  })

private class Fixture(authenticateFails: Boolean = false) {
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
    LoginCommand(
      method = LoginMethodKind.PASSWORD,
      subject = "Ada@Example.Test",
      password = "correct-password",
      ipAddress = "127.0.0.1",
      userAgent = "test",
    )

  val loginAccounts = mockk<LoginAccountStore>()
  val authEvents = mockk<AuthEventRepository>()
  val loginOrchestrator = mockk<LoginOrchestrator>()
  val sessionCredentialService = mockk<SessionCredentialService>()
  val bearerCredentialService = mockk<BearerCredentialService>()
  val appendedEvents = mutableListOf<CreateAuthEventCommand>()
  val service =
    AuthenticationService(
      loginAccounts = loginAccounts,
      authEvents = authEvents,
      loginOrchestrator = loginOrchestrator,
      sessionCredentialService = sessionCredentialService,
      bearerCredentialService = bearerCredentialService,
      clock = Clock.fixed(Instant.parse("2026-07-02T00:00:00Z"), ZoneOffset.UTC),
    )

  init {
    if (authenticateFails) {
      coEvery { loginOrchestrator.authenticate(any()) } throws
        AuthenticationFailedException("Invalid credentials.")
    } else {
      coEvery { loginOrchestrator.authenticate(any()) } returns
        AuthenticatedIdentity(user = user, loginAccount = loginAccount)
    }
    coEvery { sessionCredentialService.issueSession(any(), any(), any(), any()) } returns
      IssuedCredential(
        id = UUID.randomUUID(),
        apiId = null,
        secret = "secret-1",
        expiresAt = now.plusHours(12),
      )
    coEvery { loginAccounts.touchLastUsed(loginAccount.id, any()) } returns true
    coEvery { authEvents.append(any<CreateAuthEventCommand>()) } answers
      {
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
