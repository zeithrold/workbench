package ink.doa.workbench.security.identity

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.common.summary.LoginMethodSummary
import ink.doa.workbench.core.common.summary.TenantSummary
import ink.doa.workbench.core.identity.LoginDiscoveryRepository
import ink.doa.workbench.core.identity.LoginMethodRepository
import ink.doa.workbench.core.identity.TenantMemberRepository
import ink.doa.workbench.core.identity.TenantRepository
import ink.doa.workbench.core.identity.model.AuthenticatedIdentity
import ink.doa.workbench.core.identity.model.LoginAccountRecord
import ink.doa.workbench.core.identity.model.LoginCommand
import ink.doa.workbench.core.identity.model.LoginMethodDefinitionRecord
import ink.doa.workbench.core.identity.model.LoginMethodKind
import ink.doa.workbench.core.identity.model.TenantLoginOption
import ink.doa.workbench.core.identity.model.TenantMemberRecord
import ink.doa.workbench.core.identity.model.TenantMemberStatus
import ink.doa.workbench.core.identity.model.TenantRecord
import ink.doa.workbench.core.identity.model.TenantStatus
import ink.doa.workbench.core.identity.model.UserRecord
import ink.doa.workbench.core.permission.AdminUserQueryRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking

class LoginCompletionServiceTest :
  StringSpec({
    val loginMethods = mockk<LoginMethodRepository>()
    val loginDiscovery = mockk<LoginDiscoveryRepository>()
    val tenantMembers = mockk<TenantMemberRepository>()
    val tenants = mockk<TenantRepository>()
    val adminUserQueries = mockk<AdminUserQueryRepository>(relaxed = true)
    val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
    val service =
      LoginCompletionService(
        loginMethods,
        loginDiscovery,
        tenantMembers,
        tenants,
        adminUserQueries,
        clock,
      )

    "instance_password returns INSTANCE context" {
      val user = sampleUser()
      val loginMethod = sampleLoginMethod("instance_password")
      val loginAccount = sampleLoginAccount(loginMethod.id)
      val identity = AuthenticatedIdentity(user = user, loginAccount = loginAccount)
      val command =
        LoginCommand(
          method = LoginMethodKind.PASSWORD,
          subject = user.primaryEmail,
          password = "secret",
        )
      coEvery { loginMethods.findLoginMethodById(loginMethod.id) } returns loginMethod

      val result = runBlocking { service.resolve(identity, command) }

      result.loginContext shouldBe LoginContext.INSTANCE
      result.activeTenantId shouldBe null
      result.activeTenant shouldBe null
      result.eligibleTenants.shouldBeEmpty()
    }

    "single eligible tenant returns TENANT with active tenant" {
      val user = sampleUser()
      val tenant = sampleTenant()
      val loginMethod = sampleLoginMethod("password")
      val loginAccount = sampleLoginAccount(loginMethod.id)
      val identity = AuthenticatedIdentity(user = user, loginAccount = loginAccount)
      val command =
        LoginCommand(
          method = LoginMethodKind.PASSWORD,
          subject = user.primaryEmail,
          password = "secret",
        )
      val option =
        TenantLoginOption(
          tenant = TenantSummary.from(tenant),
          loginMethod = LoginMethodSummary.from(loginMethod),
        )
      coEvery { loginMethods.findLoginMethodById(loginMethod.id) } returns loginMethod
      coEvery { loginDiscovery.listLoginOptionsForIdentifier(user.primaryEmail!!) } returns
        listOf(option)
      coEvery { tenantMembers.listByUser(user.id) } returns
        listOf(sampleMembership(user.id, tenant.id))
      coEvery { tenants.findById(tenant.id) } returns tenant
      coEvery { tenants.findByApiId(tenant.apiId.value) } returns tenant

      val result = runBlocking { service.resolve(identity, command) }

      result.loginContext shouldBe LoginContext.TENANT
      result.activeTenantId shouldBe tenant.id
      result.activeTenant shouldBe TenantSummary.from(tenant)
      result.eligibleTenants.shouldBeEmpty()
    }

    "multiple eligible tenants returns eligible list" {
      val user = sampleUser()
      val tenantA = sampleTenant()
      val tenantB =
        sampleTenant()
          .copy(
            id = UUID.randomUUID(),
            apiId = PublicId.new("ten"),
            slug = "beta",
            name = "Beta",
          )
      val loginMethod = sampleLoginMethod("password")
      val loginAccount = sampleLoginAccount(loginMethod.id)
      val identity = AuthenticatedIdentity(user = user, loginAccount = loginAccount)
      val command =
        LoginCommand(
          method = LoginMethodKind.PASSWORD,
          subject = user.primaryEmail,
          password = "secret",
        )
      val options =
        listOf(
          TenantLoginOption(
            tenant = TenantSummary.from(tenantA),
            loginMethod = LoginMethodSummary.from(loginMethod),
          ),
          TenantLoginOption(
            tenant = TenantSummary.from(tenantB),
            loginMethod = LoginMethodSummary.from(loginMethod),
          ),
        )
      coEvery { loginMethods.findLoginMethodById(loginMethod.id) } returns loginMethod
      coEvery { loginDiscovery.listLoginOptionsForIdentifier(user.primaryEmail!!) } returns options
      coEvery { tenantMembers.listByUser(user.id) } returns
        listOf(
          sampleMembership(user.id, tenantA.id),
          sampleMembership(user.id, tenantB.id),
        )
      coEvery { tenants.findById(tenantA.id) } returns tenantA
      coEvery { tenants.findById(tenantB.id) } returns tenantB

      val result = runBlocking { service.resolve(identity, command) }

      result.loginContext shouldBe LoginContext.TENANT
      result.activeTenantId shouldBe null
      result.activeTenant shouldBe null
      result.eligibleTenants.map { it.id }.toSet() shouldBe setOf(tenantA.apiId, tenantB.apiId)
    }

    "adminScopes returns instance and tenant scopes" {
      val userId = UUID.randomUUID()
      val tenantId = UUID.randomUUID()
      coEvery {
        adminUserQueries.isActiveInstanceAdmin(
          userId,
          OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        )
      } returns true
      coEvery { adminUserQueries.listByUser(userId) } returns
        listOf(
          ink.doa.workbench.core.permission.AdminUserRecord(
            id = UUID.randomUUID(),
            apiId = PublicId.new("adm"),
            userId = userId,
            scope = ink.doa.workbench.core.permission.AdminScope.TENANT,
            tenantId = tenantId,
            status = ink.doa.workbench.core.permission.AdminUserStatus.ACTIVE,
            grantedBy = null,
            validFrom = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
            validTo = null,
            createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
            updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
          )
        )

      val scopes = runBlocking { service.adminScopes(userId) }

      scopes shouldBe listOf("instance", "tenant:$tenantId")
    }
  })

private fun sampleUser(): UserRecord =
  UserRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("usr"),
    displayName = "Ada",
    primaryEmail = "ada@example.test",
  )

private fun sampleTenant(): TenantRecord {
  val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")
  return TenantRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("ten"),
    slug = "acme",
    name = "Acme",
    status = TenantStatus.ACTIVE,
    createdAt = now,
    updatedAt = now,
  )
}

private fun sampleLoginMethod(code: String): LoginMethodDefinitionRecord {
  val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")
  return LoginMethodDefinitionRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("lmd"),
    code = code,
    kind = LoginMethodKind.PASSWORD,
    name = code,
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
    subject = "ada@example.test",
    normalizedSubject = "ada@example.test",
    displayName = "Ada",
    lastUsedAt = null,
    disabledAt = null,
    disabledBy = null,
    createdAt = now,
    updatedAt = now,
  )
}

private fun sampleMembership(userId: UUID, tenantId: UUID): TenantMemberRecord {
  val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")
  return TenantMemberRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("tmb"),
    tenantId = tenantId,
    userId = userId,
    status = TenantMemberStatus.ACTIVE,
    joinedAt = now,
    invitedBy = null,
    createdAt = now,
    updatedAt = now,
  )
}
