package one.ztd.workbench.identity

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
import one.ztd.workbench.identity.common.summary.LoginMethodSummary
import one.ztd.workbench.identity.model.AuthenticatedIdentity
import one.ztd.workbench.identity.model.LoginAccountRecord
import one.ztd.workbench.identity.model.LoginCommand
import one.ztd.workbench.identity.model.LoginMethodDefinitionRecord
import one.ztd.workbench.identity.model.LoginMethodKind
import one.ztd.workbench.identity.model.TenantLoginOption
import one.ztd.workbench.identity.model.TenantMemberRecord
import one.ztd.workbench.identity.model.TenantMemberStatus
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.identity.permission.AdminUserQueryRepository
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.tenant.TenantRepository
import one.ztd.workbench.tenant.common.summary.TenantSummary
import one.ztd.workbench.tenant.model.TenantRecord
import one.ztd.workbench.tenant.model.TenantStatus

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
          one.ztd.workbench.identity.permission.AdminUserRecord(
            id = UUID.randomUUID(),
            apiId = PublicId.new("adm"),
            userId = userId,
            scope = one.ztd.workbench.identity.permission.AdminScope.TENANT,
            tenantId = tenantId,
            status = one.ztd.workbench.identity.permission.AdminUserStatus.ACTIVE,
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
