package one.ztd.workbench.identity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import one.ztd.workbench.identity.auth.AuthenticationService
import one.ztd.workbench.identity.auth.BearerCredentialService
import one.ztd.workbench.identity.auth.CreateManagedBearerTokenCommand
import one.ztd.workbench.identity.auth.LoginCompletionRequest
import one.ztd.workbench.identity.common.IdentityPublicIdResolver
import one.ztd.workbench.identity.common.summary.UserSummary
import one.ztd.workbench.identity.model.AuthenticatedIdentity
import one.ztd.workbench.identity.model.AuthenticatedPrincipal
import one.ztd.workbench.identity.model.AuthenticationResult
import one.ztd.workbench.identity.model.IssuedCredential
import one.ztd.workbench.identity.model.LoginAccountRecord
import one.ztd.workbench.identity.model.LoginCommand
import one.ztd.workbench.identity.model.LoginMethodKind
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.kernel.common.errors.AuthenticationFailedException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.tenant.common.summary.TenantSummary
import one.ztd.workbench.tenant.model.TenantRecord
import one.ztd.workbench.tenant.model.TenantStatus

class AuthApplicationServiceTest :
  StringSpec({
    val authenticationService = mockk<AuthenticationService>()
    val sessionService = mockk<SessionService>()
    val loginCompletionService = mockk<LoginCompletionService>()
    val bearerCredentialService = mockk<BearerCredentialService>()
    val publicIds = mockk<IdentityPublicIdResolver>()
    val service =
      AuthApplicationService(
        authenticationService,
        sessionService,
        loginCompletionService,
        bearerCredentialService,
        publicIds,
      )

    beforeTest {
      clearMocks(
        authenticationService,
        sessionService,
        loginCompletionService,
        bearerCredentialService,
        publicIds,
        answers = false,
        recordedCalls = true,
      )
    }

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
          LoginCompletionRequest(
            identity = identity,
            issueBearerToken = command.issueBearerToken,
            ipAddress = command.ipAddress,
            userAgent = command.userAgent,
            tenantIdForAudit = tenant.id,
            activeTenantId = tenant.id,
          )
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

    "logout skips revocation when credentials are absent" {
      val client = ClientContext(ipAddress = "127.0.0.1", userAgent = "test")

      runBlocking { service.logout(client, sessionSecret = null, bearerToken = null) }

      coVerify(exactly = 0) { authenticationService.logoutSession(any(), any(), any()) }
      coVerify(exactly = 0) { bearerCredentialService.revokeBearerToken(any(), any(), any()) }
    }

    "issueBearerToken resolves tenant from public id and returns issued view" {
      val user = sampleUser()
      val tenant = sampleTenant()
      val loginAccountId = UUID.randomUUID()
      val principal =
        AuthenticatedPrincipal(
          user = user,
          loginAccountId = loginAccountId,
          sessionId = UUID.randomUUID().toString(),
          bearerTokenId = null,
        )
      val client = ClientContext(ipAddress = "127.0.0.1", userAgent = "test")
      val issued =
        IssuedCredential(
          id = UUID.randomUUID(),
          apiId = PublicId.new("btk"),
          secret = "issued-secret",
          expiresAt = OffsetDateTime.parse("2026-07-05T00:00:00Z"),
        )

      coEvery { publicIds.resolveTenant(tenant.apiId.value) } returns tenant
      coEvery {
        bearerCredentialService.createBearerToken(
          CreateManagedBearerTokenCommand(
            userId = user.id,
            loginAccountId = loginAccountId,
            tenantId = tenant.id,
            name = "ci-token",
            scopes = setOf("workbench.api"),
            ipAddress = client.ipAddress,
            userAgent = client.userAgent,
          )
        )
      } returns issued

      val view = runBlocking {
        service.issueBearerToken(
          principal = principal,
          tenantId = tenant.apiId.value,
          name = "ci-token",
          scopes = listOf("workbench.api"),
          client = client,
        )
      }

      view.id shouldBe issued.apiId!!.value
      view.token shouldBe "issued-secret"
      view.expiresAt shouldBe issued.expiresAt
    }

    "issueBearerToken uses active session tenant when tenant id is omitted" {
      val user = sampleUser()
      val tenant = sampleTenant()
      val loginAccountId = UUID.randomUUID()
      val principal =
        AuthenticatedPrincipal(
          user = user,
          loginAccountId = loginAccountId,
          sessionId = UUID.randomUUID().toString(),
          bearerTokenId = null,
        )
      val client = ClientContext(ipAddress = "127.0.0.1", userAgent = "test")
      val issued =
        IssuedCredential(
          id = UUID.randomUUID(),
          apiId = PublicId.new("btk"),
          secret = "session-tenant-secret",
          expiresAt = OffsetDateTime.parse("2026-07-05T00:00:00Z"),
        )

      coEvery { sessionService.requireActiveTenantId(principal) } returns tenant.id
      coEvery {
        bearerCredentialService.createBearerToken(
          CreateManagedBearerTokenCommand(
            userId = user.id,
            loginAccountId = loginAccountId,
            tenantId = tenant.id,
            name = null,
            scopes = emptySet(),
            ipAddress = client.ipAddress,
            userAgent = client.userAgent,
          )
        )
      } returns issued

      val view = runBlocking {
        service.issueBearerToken(
          principal = principal,
          tenantId = null,
          name = null,
          scopes = emptyList(),
          client = client,
        )
      }

      view.token shouldBe "session-tenant-secret"
    }

    "issueBearerToken rejects principals without login account" {
      val principal =
        AuthenticatedPrincipal(
          user = sampleUser(),
          loginAccountId = null,
          sessionId = UUID.randomUUID().toString(),
          bearerTokenId = null,
        )

      shouldThrow<AuthenticationFailedException> {
          runBlocking {
            service.issueBearerToken(
              principal = principal,
              tenantId = null,
              name = null,
              scopes = emptyList(),
              client = ClientContext(ipAddress = null, userAgent = null),
            )
          }
        }
        .errorCode shouldBe WorkbenchErrorCode.AUTH_AUTHENTICATION_REQUIRED
    }

    "revokeBearerToken delegates to bearer credential service" {
      val user = sampleUser()
      val principal =
        AuthenticatedPrincipal(
          user = user,
          loginAccountId = UUID.randomUUID(),
          sessionId = UUID.randomUUID().toString(),
          bearerTokenId = null,
        )
      val client = ClientContext(ipAddress = "127.0.0.1", userAgent = "test")
      val tokenPublicId = PublicId.new("btk").value

      coEvery {
        bearerCredentialService.revokeBearerTokenByApiId(
          tokenApiId = tokenPublicId,
          actorUserId = user.id,
          ipAddress = client.ipAddress,
          userAgent = client.userAgent,
        )
      } returns true

      runBlocking { service.revokeBearerToken(principal, tokenPublicId, client) }

      coVerify(exactly = 1) {
        bearerCredentialService.revokeBearerTokenByApiId(
          tokenApiId = tokenPublicId,
          actorUserId = user.id,
          ipAddress = client.ipAddress,
          userAgent = client.userAgent,
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
