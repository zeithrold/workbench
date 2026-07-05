package ink.doa.workbench.security.identity.auth

import ink.doa.workbench.core.common.errors.AuthenticationFailedException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.AuthEventRepository
import ink.doa.workbench.core.identity.LoginAccountStore
import ink.doa.workbench.core.identity.model.AuditEventResult
import ink.doa.workbench.core.identity.model.AuthEventRecord
import ink.doa.workbench.core.identity.model.AuthEventType
import ink.doa.workbench.core.identity.model.AuthenticatedIdentity
import ink.doa.workbench.core.identity.model.CreateAuthEventCommand
import ink.doa.workbench.core.identity.model.IssuedCredential
import ink.doa.workbench.core.identity.model.LoginAccountRecord
import ink.doa.workbench.core.identity.model.LoginCommand
import ink.doa.workbench.core.identity.model.LoginMethodKind
import ink.doa.workbench.core.identity.model.UserRecord
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

    "login with bearer token issues token credential" {
      val fixture = Fixture()
      coEvery { fixture.bearerCredentialService.issueBearerToken(any()) } returns
        IssuedCredential(
          id = UUID.randomUUID(),
          apiId = PublicId.new("btk"),
          secret = "bearer-secret",
          expiresAt = Fixture.now.plusDays(30),
        )

      val result = runBlocking {
        fixture.service.login(fixture.loginCommand.copy(issueBearerToken = true))
      }

      result.bearerToken?.secret shouldBe "bearer-secret"
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
        AuthenticationFailedException(WorkbenchErrorCode.AUTH_INVALID_CREDENTIALS)
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
        firstArg<CreateAuthEventCommand>().let { event ->
          appendedEvents += event
          AuthEventRecord(
            id = UUID.randomUUID(),
            authEventId = PublicId.new("aut"),
            tenantId = event.tenantId,
            userId = event.userId,
            loginAccountId = event.loginAccountId,
            loginMethodId = event.loginMethodId,
            eventType = event.eventType,
            result = event.result,
            failureReason = event.failureReason,
            ipAddress = event.ipAddress,
            userAgent = event.userAgent,
            metadata = event.metadata,
            occurredAt = now,
          )
        }
      }
  }

  companion object {
    val now: OffsetDateTime = OffsetDateTime.parse("2026-07-02T00:00:00Z")
  }
}
