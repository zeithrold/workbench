package ink.doa.workbench.identity.auth

import ink.doa.workbench.identity.model.AuthenticatedIdentity
import ink.doa.workbench.identity.model.LoginAccountRecord
import ink.doa.workbench.identity.model.LoginCommand
import ink.doa.workbench.identity.model.LoginMethodKind
import ink.doa.workbench.identity.model.UserRecord
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import ink.doa.workbench.kernel.common.ids.PublicId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking

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
