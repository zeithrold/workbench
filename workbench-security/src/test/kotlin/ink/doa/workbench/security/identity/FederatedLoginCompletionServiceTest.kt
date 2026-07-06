package ink.doa.workbench.security.identity

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.common.summary.TenantSummary
import ink.doa.workbench.core.common.summary.UserSummary
import ink.doa.workbench.core.identity.model.AuthenticatedIdentity
import ink.doa.workbench.core.identity.model.AuthenticatedPrincipal
import ink.doa.workbench.core.identity.model.AuthenticationResult
import ink.doa.workbench.core.identity.model.CredentialType
import ink.doa.workbench.core.identity.model.IssuedCredential
import ink.doa.workbench.core.identity.model.LoginAccountRecord
import ink.doa.workbench.core.identity.model.UserRecord
import ink.doa.workbench.security.identity.auth.AuthenticationService
import ink.doa.workbench.security.identity.auth.FederatedLoginResult
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking

class FederatedLoginCompletionServiceTest :
  StringSpec({
    val authenticationService = mockk<AuthenticationService>()
    val sessionService = mockk<SessionService>()
    val service = FederatedLoginCompletionService(authenticationService, sessionService)

    "complete returns tenant login view" {
      val tenantId = UUID.randomUUID()
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
          loginMethodId = UUID.randomUUID(),
          subject = "ada@example.test",
          normalizedSubject = "ada@example.test",
          displayName = "Ada",
          lastUsedAt = null,
          disabledAt = null,
          disabledBy = null,
          createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
          updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        )
      val tenantSummary = TenantSummary(id = PublicId.new("ten"), slug = "acme", name = "Acme")
      val session =
        IssuedCredential(
          id = UUID.randomUUID(),
          apiId = null,
          secret = "session-secret",
          expiresAt = OffsetDateTime.parse("2026-07-04T12:00:00Z"),
        )
      coEvery { authenticationService.completeLogin(any()) } returns
        AuthenticationResult(
          principal =
            AuthenticatedPrincipal(
              user = user,
              loginAccountId = account.id,
              sessionId = session.id.toString(),
              bearerTokenId = null,
              credentialType = CredentialType.SESSION,
            ),
          session = session,
          bearerToken = null,
        )
      coEvery { sessionService.tenantSummary(tenantId) } returns tenantSummary

      val view = runBlocking {
        service.complete(
          FederatedLoginResult(
            identity = AuthenticatedIdentity(user = user, loginAccount = account),
            tenantId = tenantId,
          ),
          ClientContext(ipAddress = "127.0.0.1", userAgent = "agent"),
        )
      }

      view.user shouldBe UserSummary.from(user)
      view.sessionSecret shouldBe "session-secret"
      view.activeTenant shouldBe tenantSummary
      view.loginContext shouldBe LoginContext.TENANT
    }
  })
