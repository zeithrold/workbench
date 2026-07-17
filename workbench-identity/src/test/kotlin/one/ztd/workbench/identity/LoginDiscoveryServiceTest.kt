package one.ztd.workbench.identity

import io.kotest.core.spec.style.StringSpec
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
import one.ztd.workbench.identity.model.LoginMethodDefinitionRecord
import one.ztd.workbench.identity.model.LoginMethodKind
import one.ztd.workbench.identity.model.TenantLoginOption
import one.ztd.workbench.identity.model.TenantMemberRecord
import one.ztd.workbench.identity.model.TenantMemberStatus
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.identity.permission.AdminUserQueryRepository
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.tenant.common.summary.TenantSummary
import one.ztd.workbench.tenant.model.TenantRecord
import one.ztd.workbench.tenant.model.TenantStatus

class LoginDiscoveryServiceTest :
  StringSpec({
    val users = mockk<UserRepository>()
    val loginDiscovery = mockk<LoginDiscoveryRepository>()
    val loginMethods = mockk<LoginMethodRepository>()
    val tenantMembers = mockk<TenantMemberRepository>()
    val adminUserQueries = mockk<AdminUserQueryRepository>()
    val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
    val service =
      LoginDiscoveryService(
        users,
        loginDiscovery,
        loginMethods,
        tenantMembers,
        adminUserQueries,
        clock,
      )

    "discover returns unrecognized view for unknown identifier" {
      coEvery { users.findByPrimaryEmail("unknown@example.test") } returns null
      coEvery {
        loginDiscovery.findUserByMethodAndSubject("password", "unknown@example.test")
      } returns null
      coEvery {
        loginDiscovery.findUserByMethodAndSubject("instance_password", "unknown@example.test")
      } returns null

      val result = runBlocking { service.discover("unknown@example.test") }

      result.identifierRecognized shouldBe false
      result.flow shouldBe LoginFlow.TENANT
      result.tenantMethods shouldBe emptyList()
    }

    "discover returns instance-only flow for instance admin without memberships" {
      val user = sampleUser()
      val instanceMethod = sampleLoginMethod("instance_password")
      coEvery { users.findByPrimaryEmail(user.primaryEmail!!) } returns user
      coEvery { tenantMembers.listByUser(user.id) } returns emptyList()
      coEvery {
        adminUserQueries.isActiveInstanceAdmin(
          user.id,
          OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        )
      } returns true
      coEvery { loginMethods.findLoginMethodByCode("instance_password") } returns instanceMethod

      val result = runBlocking { service.discover(user.primaryEmail!!) }

      result.identifierRecognized shouldBe true
      result.flow shouldBe LoginFlow.INSTANCE_ONLY
      result.instancePasswordMethod shouldBe LoginMethodSummary.from(instanceMethod)
    }

    "discover returns tenant flow with grouped login methods" {
      val user = sampleUser()
      val tenant = sampleTenant()
      val loginMethod = sampleLoginMethod("password")
      val option =
        TenantLoginOption(
          tenant = TenantSummary.from(tenant),
          loginMethod = LoginMethodSummary.from(loginMethod),
        )
      coEvery { users.findByPrimaryEmail(user.primaryEmail!!) } returns user
      coEvery { tenantMembers.listByUser(user.id) } returns
        listOf(sampleMembership(user.id, tenant.id))
      coEvery {
        adminUserQueries.isActiveInstanceAdmin(
          user.id,
          OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        )
      } returns false
      coEvery { loginDiscovery.listLoginOptionsForIdentifier(user.primaryEmail!!) } returns
        listOf(option)

      val result = runBlocking { service.discover(user.primaryEmail!!) }

      result.identifierRecognized shouldBe true
      result.flow shouldBe LoginFlow.TENANT
      result.tenantMethods.single().supportedTenants.single().id shouldBe tenant.apiId
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
