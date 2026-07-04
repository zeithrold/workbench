package ink.doa.workbench.security.identity

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.common.summary.TenantSummary
import ink.doa.workbench.core.common.summary.UserSummary
import ink.doa.workbench.core.identity.model.AuthenticatedIdentity
import ink.doa.workbench.core.identity.model.AuthenticatedPrincipal
import ink.doa.workbench.core.identity.model.AuthenticationResult
import ink.doa.workbench.core.identity.model.IssuedCredential
import ink.doa.workbench.core.identity.model.LoginAccountRecord
import ink.doa.workbench.core.identity.model.LoginCommand
import ink.doa.workbench.core.identity.model.LoginMethodKind
import ink.doa.workbench.core.identity.model.TenantRecord
import ink.doa.workbench.core.identity.model.TenantStatus
import ink.doa.workbench.core.identity.model.UserRecord
import ink.doa.workbench.security.common.PublicIdResolver
import ink.doa.workbench.security.identity.auth.AuthenticationService
import ink.doa.workbench.security.identity.auth.BearerCredentialService
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking

class AuthApplicationServiceTest :
  StringSpec({
    val authenticationService = mockk<AuthenticationService>()
    val sessionService = mockk<SessionService>()
    val loginCompletionService = mockk<LoginCompletionService>()
    val bearerCredentialService = mockk<BearerCredentialService>()
    val publicIds = mockk<PublicIdResolver>()
    val service =
      AuthApplicationService(
        authenticationService,
        sessionService,
        loginCompletionService,
        bearerCredentialService,
        publicIds,
      )

    "login returns login view with session and tenant context" {
      val user = sampleUser()
      val tenant = sampleTenant()
      val loginAccount =
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
      val identity = AuthenticatedIdentity(user = user, loginAccount = loginAccount)
      val command =
        LoginCommand(
          method = LoginMethodKind.PASSWORD,
          subject = "ada@example.test",
          password = "secret",
          ipAddress = "127.0.0.1",
          userAgent = "test",
        )
      val sessionId = UUID.randomUUID()
      val authResult =
        AuthenticationResult(
          principal =
            AuthenticatedPrincipal(
              user = user,
              loginAccountId = loginAccount.id,
              sessionId = sessionId.toString(),
              bearerTokenId = null,
            ),
          session =
            IssuedCredential(
              id = sessionId,
              apiId = PublicId.new("ses"),
              secret = "session-secret",
              expiresAt = OffsetDateTime.parse("2026-07-05T00:00:00Z"),
            ),
          bearerToken = null,
        )
      val completion =
        LoginCompletion(
          loginContext = LoginContext.TENANT,
          activeTenantId = tenant.id,
          activeTenant = TenantSummary.from(tenant),
          eligibleTenants = listOf(TenantSummary.from(tenant)),
        )

      coEvery { authenticationService.authenticate(command) } returns identity
      coEvery { loginCompletionService.resolve(identity, command) } returns completion
      coEvery {
        authenticationService.completeLogin(
          identity = identity,
          issueBearerToken = command.issueBearerToken,
          ipAddress = command.ipAddress,
          userAgent = command.userAgent,
          tenantIdForAudit = tenant.id,
          activeTenantId = tenant.id,
        )
      } returns authResult

      val view = runBlocking { service.login(command) }

      view.user shouldBe UserSummary.from(user)
      view.sessionSecret shouldBe "session-secret"
      view.activeTenant shouldBe TenantSummary.from(tenant)
    }

    "logout revokes session and bearer token when provided" {
      val client = ClientContext(ipAddress = "127.0.0.1", userAgent = "test")
      coEvery { authenticationService.logoutSession("session-secret", any(), any()) } returns true
      coEvery {
        bearerCredentialService.revokeBearerToken("bearer-token", any(), any())
      } returns true

      runBlocking {
        service.logout(client, sessionSecret = "session-secret", bearerToken = "bearer-token")
      }

      coVerify(exactly = 1) {
        authenticationService.logoutSession("session-secret", client.ipAddress, client.userAgent)
      }
      coVerify(exactly = 1) {
        bearerCredentialService.revokeBearerToken(
          "bearer-token",
          client.ipAddress,
          client.userAgent,
        )
      }
    }
  })

private fun sampleUser(): UserRecord =
  UserRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("usr"),
    displayName = "Ada",
    primaryEmail = "ada@example.test",
  )

private fun sampleTenant(): TenantRecord =
  TenantRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("ten"),
    slug = "acme",
    name = "Acme",
    status = TenantStatus.ACTIVE,
    createdAt = OffsetDateTime.now(ZoneOffset.UTC),
    updatedAt = OffsetDateTime.now(ZoneOffset.UTC),
  )
