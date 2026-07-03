package doa.ink.workbench.service.instance

import doa.ink.workbench.core.common.errors.InstanceAlreadyInitializedException
import doa.ink.workbench.core.common.errors.SetupTokenInvalidException
import doa.ink.workbench.core.common.ids.PublicId
import doa.ink.workbench.core.common.summary.UserSummary
import doa.ink.workbench.core.identity.LoginAccountStore
import doa.ink.workbench.core.identity.LoginMethodRepository
import doa.ink.workbench.core.identity.UserLoginAccountRepository
import doa.ink.workbench.core.identity.UserRepository
import doa.ink.workbench.core.identity.auth.PasswordHasher
import doa.ink.workbench.core.identity.model.BootstrapInstanceAdminCommand
import doa.ink.workbench.core.identity.model.LoginMethodDefinitionRecord
import doa.ink.workbench.core.identity.model.LoginMethodKind
import doa.ink.workbench.core.identity.model.UserRecord
import doa.ink.workbench.core.permission.AccessGrantRecord
import doa.ink.workbench.core.permission.AccessGrantRepository
import doa.ink.workbench.core.permission.AdminScope
import doa.ink.workbench.core.permission.AdminUserCommandRepository
import doa.ink.workbench.core.permission.AdminUserQueryRepository
import doa.ink.workbench.core.permission.AdminUserRecord
import doa.ink.workbench.core.permission.AdminUserStatus
import doa.ink.workbench.service.identity.AuthApplicationService
import doa.ink.workbench.service.identity.LoginView
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

class InstanceSetupServiceTest :
  StringSpec({
    "setup status reflects whether an instance admin exists" {
      val adminUserQueries = mockk<AdminUserQueryRepository>()
      coEvery { adminUserQueries.existsActiveInstanceAdmin() } returns false
      val service = service(adminUserQueries = adminUserQueries)

      runBlocking { service.setupStatus().initialized } shouldBe false
    }

    "bootstrap rejects when instance is already initialized" {
      val adminUserQueries = mockk<AdminUserQueryRepository>()
      coEvery { adminUserQueries.existsActiveInstanceAdmin() } returns true
      val service = service(adminUserQueries = adminUserQueries)

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
      val adminUserQueries = mockk<AdminUserQueryRepository>()
      coEvery { adminUserQueries.existsActiveInstanceAdmin() } returns false
      val service =
        service(
          adminUserQueries = adminUserQueries,
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

    "bootstrap creates instance admin and signs in" {
      val users = mockk<UserRepository>()
      val loginMethods = mockk<LoginMethodRepository>()
      val loginAccounts = mockk<LoginAccountStore>()
      val userLoginAccounts = mockk<UserLoginAccountRepository>()
      val adminUserCommands = mockk<AdminUserCommandRepository>()
      val adminUserQueries = mockk<AdminUserQueryRepository>()
      val accessGrants = mockk<AccessGrantRepository>()
      val authApplicationService = mockk<AuthApplicationService>()
      val passwordHasher = mockk<PasswordHasher>()
      val instancePasswordMethod =
        LoginMethodDefinitionRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("lmg"),
          code = "instance_password",
          kind = LoginMethodKind.PASSWORD,
          name = "Workbench Admin",
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
        )
      val loginView =
        LoginView(
          user = UserSummary.from(createdUser),
          sessionExpiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(12),
          sessionSecret = "session-secret",
          bearerToken = null,
        )
      val adminRecord =
        AdminUserRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("adu"),
          userId = createdUser.id,
          scope = AdminScope.INSTANCE,
          tenantId = null,
          status = AdminUserStatus.ACTIVE,
          grantedBy = createdUser.id,
          validFrom = OffsetDateTime.now(ZoneOffset.UTC),
          validTo = null,
          createdAt = OffsetDateTime.now(ZoneOffset.UTC),
          updatedAt = OffsetDateTime.now(ZoneOffset.UTC),
        )

      coEvery { adminUserQueries.existsActiveInstanceAdmin() } returns false
      coEvery { loginMethods.findLoginMethodByCode("instance_password") } returns
        instancePasswordMethod
      coEvery { users.create(any()) } returns createdUser
      coEvery { loginAccounts.createLoginAccount(any()) } returns mockk(relaxed = true)
      coEvery { passwordHasher.hash("secure-password-1") } returns "bcrypt-hash"
      coEvery { loginAccounts.upsertParameter(any()) } returns mockk(relaxed = true)
      coEvery { userLoginAccounts.linkUser(any()) } returns mockk(relaxed = true)
      coEvery { adminUserCommands.create(any()) } returns adminRecord
      coEvery { accessGrants.create(any()) } returns mockk<AccessGrantRecord>(relaxed = true)
      coEvery { authApplicationService.login(any()) } returns loginView

      val service =
        service(
          users = users,
          loginMethods = loginMethods,
          loginAccounts = loginAccounts,
          userLoginAccounts = userLoginAccounts,
          adminUserCommands = adminUserCommands,
          adminUserQueries = adminUserQueries,
          accessGrants = accessGrants,
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
      result.loginMethod.id shouldBe instancePasswordMethod.apiId.value
      result.session.sessionSecret shouldBe "session-secret"
      coVerify(exactly = 1) { adminUserCommands.create(any()) }
      coVerify(exactly = 4) { accessGrants.create(any()) }
    }
  })

private fun service(
  users: UserRepository = mockk(),
  loginMethods: LoginMethodRepository = mockk(),
  loginAccounts: LoginAccountStore = mockk(),
  userLoginAccounts: UserLoginAccountRepository = mockk(),
  adminUserCommands: AdminUserCommandRepository = mockk(),
  adminUserQueries: AdminUserQueryRepository = mockk(),
  accessGrants: AccessGrantRepository = mockk(),
  passwordHasher: PasswordHasher = mockk(),
  instanceProperties: InstanceProperties = InstanceProperties(),
  authApplicationService: AuthApplicationService = mockk(),
): InstanceSetupService =
  InstanceSetupService(
    users = users,
    loginMethods = loginMethods,
    loginAccounts = loginAccounts,
    userLoginAccounts = userLoginAccounts,
    adminUserCommands = adminUserCommands,
    adminUserQueries = adminUserQueries,
    accessGrants = accessGrants,
    passwordHasher = passwordHasher,
    instanceProperties = instanceProperties,
    authApplicationService = authApplicationService,
    clock = Clock.systemUTC(),
  )
