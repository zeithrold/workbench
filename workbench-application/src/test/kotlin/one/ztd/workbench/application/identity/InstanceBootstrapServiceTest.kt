package one.ztd.workbench.application.identity

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
import one.ztd.workbench.identity.BootstrapAccountSupport
import one.ztd.workbench.identity.BootstrapAdminSupport
import one.ztd.workbench.identity.LoginAccountStore
import one.ztd.workbench.identity.LoginMethodRepository
import one.ztd.workbench.identity.UserLoginAccountRepository
import one.ztd.workbench.identity.UserRepository
import one.ztd.workbench.identity.auth.PasswordHasher
import one.ztd.workbench.identity.model.BootstrapInstanceAdminCommand
import one.ztd.workbench.identity.model.LoginAccountRecord
import one.ztd.workbench.identity.model.LoginMethodDefinitionRecord
import one.ztd.workbench.identity.model.LoginMethodKind
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.identity.permission.AccessGrantRepository
import one.ztd.workbench.identity.permission.AdminScope
import one.ztd.workbench.identity.permission.AdminUserCommandRepository
import one.ztd.workbench.identity.permission.AdminUserQueryRepository
import one.ztd.workbench.identity.permission.AdminUserRecord
import one.ztd.workbench.identity.permission.AdminUserStatus
import one.ztd.workbench.kernel.common.errors.InstanceAlreadyInitializedException
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.ids.PublicId

class InstanceBootstrapServiceTest :
  StringSpec({
    val users = mockk<UserRepository>()
    val loginMethods = mockk<LoginMethodRepository>()
    val loginAccounts = mockk<LoginAccountStore>()
    val userLoginAccounts = mockk<UserLoginAccountRepository>()
    val adminUserCommands = mockk<AdminUserCommandRepository>()
    val adminUserQueries = mockk<AdminUserQueryRepository>()
    val accessGrants = mockk<AccessGrantRepository>(relaxed = true)
    val passwordHasher = mockk<PasswordHasher>()
    val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
    val service =
      InstanceBootstrapService(
        BootstrapAccountSupport(
          users,
          loginMethods,
          loginAccounts,
          userLoginAccounts,
          passwordHasher,
        ),
        BootstrapAdminSupport(adminUserCommands, adminUserQueries, accessGrants),
        clock,
      )

    "isInitialized delegates to admin query repository" {
      coEvery { adminUserQueries.existsActiveInstanceAdmin() } returns true

      runBlocking { service.isInitialized() } shouldBe true
    }

    "bootstrap rejects when instance is already initialized" {
      coEvery { adminUserQueries.existsActiveInstanceAdmin() } returns true

      shouldThrow<InstanceAlreadyInitializedException> {
        runBlocking {
          service.bootstrap(
            BootstrapInstanceAdminCommand(
              displayName = "Admin",
              email = "admin@example.test",
              password = "secret",
            )
          )
        }
      }
    }

    "bootstrap rejects when instance password login method is missing" {
      coEvery { adminUserQueries.existsActiveInstanceAdmin() } returns false
      coEvery { loginMethods.findLoginMethodByCode("instance_password") } returns null

      shouldThrow<InvalidRequestException> {
        runBlocking {
          service.bootstrap(
            BootstrapInstanceAdminCommand(
              displayName = "Admin",
              email = "admin@example.test",
              password = "secret",
            )
          )
        }
      }
    }

    "bootstrap creates user, login account, admin record, and default grants" {
      val loginMethod = sampleLoginMethod()
      val user = sampleUser()
      val loginAccount = sampleLoginAccount(loginMethod.id)
      val adminRecord = sampleAdminRecord(user.id)
      coEvery { adminUserQueries.existsActiveInstanceAdmin() } returns false
      coEvery { loginMethods.findLoginMethodByCode("instance_password") } returns loginMethod
      coEvery { users.create(any()) } returns user
      coEvery { loginAccounts.createLoginAccount(any()) } returns loginAccount
      coEvery { passwordHasher.hash("secret") } returns "hashed"
      coEvery { loginAccounts.upsertParameter(any()) } returns mockk(relaxed = true)
      coEvery { userLoginAccounts.linkUser(any()) } returns mockk(relaxed = true)
      coEvery { adminUserCommands.create(any()) } returns adminRecord

      val result = runBlocking {
        service.bootstrap(
          BootstrapInstanceAdminCommand(
            displayName = "Admin",
            email = " Admin@Example.Test ",
            password = "secret",
          )
        )
      }

      result.user shouldBe user
      result.loginMethod shouldBe loginMethod
      coVerify(exactly = 9) { accessGrants.create(any()) }
    }
  })

private fun sampleUser(): UserRecord =
  UserRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("usr"),
    displayName = "Admin",
    primaryEmail = "admin@example.test",
  )

private fun sampleLoginMethod(): LoginMethodDefinitionRecord {
  val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")
  return LoginMethodDefinitionRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("lmd"),
    code = "instance_password",
    kind = LoginMethodKind.PASSWORD,
    name = "Instance Password",
    isBuiltin = true,
    isEnabledGlobally = true,
    createdAt = now,
    updatedAt = now,
  )
}

private fun sampleLoginAccount(loginMethodId: UUID): LoginAccountRecord {
  val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")
  return LoginAccountRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("lac"),
    loginMethodId = loginMethodId,
    subject = "admin@example.test",
    normalizedSubject = "admin@example.test",
    displayName = "Admin",
    lastUsedAt = null,
    disabledAt = null,
    disabledBy = null,
    createdAt = now,
    updatedAt = now,
  )
}

private fun sampleAdminRecord(userId: UUID): AdminUserRecord {
  val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")
  return AdminUserRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("adm"),
    userId = userId,
    scope = AdminScope.INSTANCE,
    tenantId = null,
    status = AdminUserStatus.ACTIVE,
    grantedBy = userId,
    validFrom = now,
    validTo = null,
    createdAt = now,
    updatedAt = now,
  )
}
