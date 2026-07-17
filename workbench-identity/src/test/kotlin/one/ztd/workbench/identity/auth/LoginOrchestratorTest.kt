package one.ztd.workbench.identity.auth

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import one.ztd.workbench.identity.model.AuthenticatedIdentity
import one.ztd.workbench.identity.model.LoginAccountRecord
import one.ztd.workbench.identity.model.LoginCommand
import one.ztd.workbench.identity.model.LoginMethodKind
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.kernel.common.ids.PublicId

class LoginOrchestratorTest :
  StringSpec({
    val passwordAuthenticator = mockk<LoginAuthenticator>()
    every { passwordAuthenticator.kind } returns LoginMethodKind.PASSWORD
    val orchestrator = LoginOrchestrator(listOf(passwordAuthenticator))

    val identity =
      AuthenticatedIdentity(
        user =
          UserRecord(
            id = UUID.randomUUID(),
            apiId = PublicId.new("usr"),
            displayName = "Ada",
            primaryEmail = "ada@example.test",
          ),
        loginAccount =
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
          ),
      )

    "authenticate delegates to matching authenticator" {
      coEvery { passwordAuthenticator.authenticate(any()) } returns identity

      val result = runBlocking {
        orchestrator.authenticate(
          LoginCommand(
            method = LoginMethodKind.PASSWORD,
            subject = "ada@example.test",
            password = "secret",
          )
        )
      }

      result shouldBe identity
    }

    "authenticate rejects federated methods" {
      shouldThrow<InvalidRequestException> {
          runBlocking {
            orchestrator.authenticate(LoginCommand(method = LoginMethodKind.OIDC))
          }
        }
        .errorCode shouldBe WorkbenchErrorCode.IDENTITY_LOGIN_METHOD_UNSUPPORTED
    }

    "authenticate rejects unsupported methods" {
      shouldThrow<InvalidRequestException> {
          runBlocking {
            orchestrator.authenticate(LoginCommand(method = LoginMethodKind.LDAP))
          }
        }
        .errorCode shouldBe WorkbenchErrorCode.IDENTITY_LOGIN_METHOD_UNSUPPORTED
    }
  })
