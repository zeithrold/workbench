package one.ztd.workbench.application.invitation

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import one.ztd.workbench.identity.AuthApplicationService
import one.ztd.workbench.identity.ClientContext
import one.ztd.workbench.identity.LocaleContextView
import one.ztd.workbench.identity.LoginContext
import one.ztd.workbench.identity.LoginView
import one.ztd.workbench.identity.SessionService
import one.ztd.workbench.identity.common.summary.UserSummary
import one.ztd.workbench.identity.model.AcceptInvitationCommand
import one.ztd.workbench.identity.model.AuthenticatedPrincipal
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.tenant.common.summary.TenantSummary

class InvitationAcceptanceApplicationServiceTest :
  StringSpec({
    val invitations = mockk<InvitationService>()
    val authentication = mockk<AuthApplicationService>()
    val sessions = mockk<SessionService>()
    val service = InvitationAcceptanceApplicationService(invitations, authentication, sessions)

    "new user acceptance creates an active tenant session" {
      val command = AcceptInvitationCommand("token", "Ada", "SecurePassword123")
      val accepted = acceptance()
      coEvery { invitations.accept(command) } returns accepted
      coEvery { authentication.login(any()) } returns
        LoginView(
          user = accepted.user,
          sessionExpiresAt = EXPIRES_AT,
          sessionSecret = "session-secret",
          bearerToken = null,
          loginContext = LoginContext.TENANT,
          activeTenant = accepted.tenant,
          localeContext = LocaleContextView(tenantDefault = "en-US"),
        )

      val result = runBlocking { service.acceptNew(command, ClientContext("127.0.0.1", "test")) }

      result.acceptance shouldBe accepted
      result.sessionSecret shouldBe "session-secret"
      coVerify {
        authentication.login(
          match {
            it.tenantId == accepted.tenant.id.value &&
              it.subject == accepted.user.primaryEmail &&
              it.password == command.password
          }
        )
      }
    }

    "existing user acceptance switches the current session" {
      val accepted = acceptance()
      val principal = principal()
      coEvery { invitations.acceptExisting("token", principal.user) } returns accepted
      coEvery { sessions.switchTenant(principal, accepted.tenant.id.value) } returns mockk()

      val result = runBlocking { service.acceptExisting("token", principal) }

      result shouldBe accepted
      coVerify { sessions.switchTenant(principal, accepted.tenant.id.value) }
    }
  }) {
  private companion object {
    val EXPIRES_AT: OffsetDateTime = OffsetDateTime.parse("2026-07-18T00:00:00Z")

    fun acceptance() =
      InvitationAcceptView(
        type = one.ztd.workbench.identity.model.InvitationType.TENANT_MEMBER,
        tenant =
          TenantSummary(
            id = PublicId("ten_01JABCDEFGHJKMNPQRSTVWXYZ0"),
            name = "Acme",
            slug = "acme",
          ),
        user =
          UserSummary(
            id = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ1"),
            displayName = "Ada",
            primaryEmail = "ada@example.test",
          ),
      )

    fun principal() =
      AuthenticatedPrincipal(
        user =
          UserRecord(
            id = UUID.randomUUID(),
            apiId = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ1"),
            displayName = "Ada",
            primaryEmail = "ada@example.test",
          ),
        loginAccountId = UUID.randomUUID(),
        sessionId = UUID.randomUUID().toString(),
        bearerTokenId = null,
      )
  }
}
