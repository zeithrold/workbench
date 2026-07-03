package doa.ink.workbench.service.instance

import doa.ink.workbench.core.common.errors.InstanceAlreadyInitializedException
import doa.ink.workbench.core.common.errors.SetupTokenInvalidException
import doa.ink.workbench.core.common.ids.PublicId
import doa.ink.workbench.core.common.summary.UserSummary
import doa.ink.workbench.core.identity.LoginAccountRepository
import doa.ink.workbench.core.identity.UserRepository
import doa.ink.workbench.core.identity.auth.PasswordHasher
import doa.ink.workbench.core.identity.model.BootstrapInstanceAdminCommand
import doa.ink.workbench.core.identity.model.LoginMethodDefinitionRecord
import doa.ink.workbench.core.identity.model.LoginMethodKind
import doa.ink.workbench.core.identity.model.UserRecord
import doa.ink.workbench.service.identity.AuthApplicationService
import doa.ink.workbench.service.identity.LoginView
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

class InstanceSetupServiceTest :
  StringSpec({
    "setup status reflects whether a system user exists" {
      val users = mockk<UserRepository>()
      coEvery { users.existsSystemUser() } returns false
      val service = service(users = users)

      runBlocking { service.setupStatus().initialized } shouldBe false
    }

    "bootstrap rejects when instance is already initialized" {
      val users = mockk<UserRepository>()
      coEvery { users.existsSystemUser() } returns true
      val service = service(users = users)

      shouldThrow<InstanceAlreadyInitializedException> {
        runBlocking {
          service.bootstrap(
            BootstrapInstanceAdminCommand(
              displayName = "Admin",
              email = "admin@example.test",
              password = "secure-password-1",
            )
          )
        }
      }
    }

    "bootstrap requires setup token when configured" {
      val users = mockk<UserRepository>()
      coEvery { users.existsSystemUser() } returns false
      val service =
        service(
          users = users,
          instanceProperties = InstanceProperties(setupToken = "expected-token"),
        )

      shouldThrow<SetupTokenInvalidException> {
        runBlocking {
          service.bootstrap(
            BootstrapInstanceAdminCommand(
              displayName = "Admin",
              email = "admin@example.test",
              password = "secure-password-1",
            )
          )
        }
      }
    }

    "bootstrap creates system user and signs in" {
      val users = mockk<UserRepository>()
      val loginAccounts = mockk<LoginAccountRepository>()
      val authApplicationService = mockk<AuthApplicationService>()
      val passwordHasher = mockk<PasswordHasher>()
      val passwordMethod =
        LoginMethodDefinitionRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("lmg"),
          code = "password",
          kind = LoginMethodKind.PASSWORD,
          name = "Password",
          isBuiltin = true,
          isEnabledGlobally = true,
          configSchema = JsonObject(emptyMap()),
          createdAt = OffsetDateTime.now(ZoneOffset.UTC),
          updatedAt = OffsetDateTime.now(ZoneOffset.UTC),
        )
      val createdUser =
        UserRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("usr"),
          displayName = "Admin",
          primaryEmail = "admin@example.test",
          isSystem = true,
        )
      val loginView =
        LoginView(
          user = UserSummary.from(createdUser),
          sessionExpiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(12),
          sessionSecret = "session-secret",
          bearerToken = null,
        )

      coEvery { users.existsSystemUser() } returns false
      coEvery { loginAccounts.findLoginMethodByCode("password") } returns passwordMethod
      coEvery { users.create(any()) } returns createdUser
      coEvery { loginAccounts.createLoginAccount(any()) } returns mockk(relaxed = true)
      coEvery { passwordHasher.hash("secure-password-1") } returns "bcrypt-hash"
      coEvery { loginAccounts.upsertParameter(any()) } returns mockk(relaxed = true)
      coEvery { loginAccounts.linkUser(any()) } returns mockk(relaxed = true)
      coEvery { authApplicationService.login(any()) } returns loginView

      val service =
        service(
          users = users,
          loginAccounts = loginAccounts,
          passwordHasher = passwordHasher,
          authApplicationService = authApplicationService,
        )

      val result = runBlocking {
        service.bootstrap(
          BootstrapInstanceAdminCommand(
            displayName = "Admin",
            email = "admin@example.test",
            password = "secure-password-1",
          )
        )
      }

      result.user.id shouldBe createdUser.apiId.value
      result.loginMethod.id shouldBe passwordMethod.apiId.value
      result.session.sessionSecret shouldBe "session-secret"
      coVerify(exactly = 1) { users.create(match { it.isSystem }) }
    }
  })

private fun service(
  users: UserRepository = mockk(),
  loginAccounts: LoginAccountRepository = mockk(),
  passwordHasher: PasswordHasher = mockk(),
  instanceProperties: InstanceProperties = InstanceProperties(),
  authApplicationService: AuthApplicationService = mockk(),
): InstanceSetupService =
  InstanceSetupService(
    users = users,
    loginAccounts = loginAccounts,
    passwordHasher = passwordHasher,
    instanceProperties = instanceProperties,
    authApplicationService = authApplicationService,
  )
