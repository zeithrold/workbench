package ink.doa.workbench.application.instance

import ink.doa.workbench.application.identity.InstanceBootstrapService
import ink.doa.workbench.identity.AuthApplicationService
import ink.doa.workbench.identity.BootstrapAccountSupport
import ink.doa.workbench.identity.BootstrapAdminSupport
import ink.doa.workbench.identity.LoginAccountStore
import ink.doa.workbench.identity.LoginMethodRepository
import ink.doa.workbench.identity.LoginView
import ink.doa.workbench.identity.UserLoginAccountRepository
import ink.doa.workbench.identity.UserRepository
import ink.doa.workbench.identity.auth.PasswordHasher
import ink.doa.workbench.identity.common.summary.UserSummary
import ink.doa.workbench.identity.model.BootstrapInstanceAdminCommand
import ink.doa.workbench.identity.model.LoginMethodDefinitionRecord
import ink.doa.workbench.identity.model.LoginMethodKind
import ink.doa.workbench.identity.model.UserRecord
import ink.doa.workbench.identity.permission.AccessGrantRecord
import ink.doa.workbench.identity.permission.AccessGrantRepository
import ink.doa.workbench.identity.permission.AdminScope
import ink.doa.workbench.identity.permission.AdminUserCommandRepository
import ink.doa.workbench.identity.permission.AdminUserQueryRepository
import ink.doa.workbench.identity.permission.AdminUserRecord
import ink.doa.workbench.identity.permission.AdminUserStatus
import ink.doa.workbench.kernel.common.errors.InstanceAlreadyInitializedException
import ink.doa.workbench.kernel.common.errors.SetupTokenInvalidException
import ink.doa.workbench.kernel.common.ids.PublicId
import ink.doa.workbench.tenant.instance.InstanceProperties
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

class InstanceSetupApplicationServiceTest :
  StringSpec({
    "setup status reflects whether an instance admin exists" {
      val adminUserQueries = mockk<AdminUserQueryRepository>()
      coEvery { adminUserQueries.existsActiveInstanceAdmin() } returns false
      val service = service(InstanceSetupServiceFixtures(adminUserQueries = adminUserQueries))

      runBlocking { service.setupStatus() } shouldBe
        InstanceSetupStatusView(initialized = false, setupTokenRequired = false)
    }

    "setup status reports when a setup token is required" {
      val adminUserQueries = mockk<AdminUserQueryRepository>()
      coEvery { adminUserQueries.existsActiveInstanceAdmin() } returns false
      val service =
        service(
          InstanceSetupServiceFixtures(
            adminUserQueries = adminUserQueries,
            instanceProperties =
              InstanceProperties(setupToken = "expected-token", id = null, name = null),
          )
        )

      runBlocking { service.setupStatus() } shouldBe
        InstanceSetupStatusView(initialized = false, setupTokenRequired = true)
    }

    "bootstrap rejects when instance is already initialized" {
      val adminUserQueries = mockk<AdminUserQueryRepository>()
      coEvery { adminUserQueries.existsActiveInstanceAdmin() } returns true
      val service = service(InstanceSetupServiceFixtures(adminUserQueries = adminUserQueries))

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
          InstanceSetupServiceFixtures(
            adminUserQueries = adminUserQueries,
            instanceProperties =
              InstanceProperties(setupToken = "expected-token", id = null, name = null),
          )
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
          InstanceSetupServiceFixtures(
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

      result.user.id shouldBe createdUser.apiId
      result.loginMethod.id shouldBe instancePasswordMethod.apiId.value
      result.session.sessionSecret shouldBe "session-secret"
      coVerify(exactly = 1) { adminUserCommands.create(any()) }
      coVerify(exactly = 9) { accessGrants.create(any()) }
    }
  })

private data class InstanceSetupServiceFixtures(
  val users: UserRepository = mockk(),
  val loginMethods: LoginMethodRepository = mockk(),
  val loginAccounts: LoginAccountStore = mockk(),
  val userLoginAccounts: UserLoginAccountRepository = mockk(),
  val adminUserCommands: AdminUserCommandRepository = mockk(),
  val adminUserQueries: AdminUserQueryRepository = mockk(),
  val accessGrants: AccessGrantRepository = mockk(),
  val passwordHasher: PasswordHasher = mockk(),
  val instanceProperties: InstanceProperties =
    InstanceProperties(setupToken = null, id = null, name = null),
  val authApplicationService: AuthApplicationService = mockk(),
)

private fun service(
  fixtures: InstanceSetupServiceFixtures = InstanceSetupServiceFixtures()
): InstanceSetupApplicationService =
  InstanceSetupApplicationService(
    instanceBootstrapService =
      InstanceBootstrapService(
        accounts =
          BootstrapAccountSupport(
            users = fixtures.users,
            loginMethods = fixtures.loginMethods,
            loginAccounts = fixtures.loginAccounts,
            userLoginAccounts = fixtures.userLoginAccounts,
            passwordHasher = fixtures.passwordHasher,
          ),
        admin =
          BootstrapAdminSupport(
            adminUserCommands = fixtures.adminUserCommands,
            adminUserQueries = fixtures.adminUserQueries,
            accessGrants = fixtures.accessGrants,
          ),
        clock = Clock.systemUTC(),
      ),
    instanceProperties = fixtures.instanceProperties,
    authApplicationService = fixtures.authApplicationService,
  )
