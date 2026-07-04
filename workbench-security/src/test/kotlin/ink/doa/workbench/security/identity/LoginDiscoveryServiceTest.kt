package ink.doa.workbench.security.identity

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.common.summary.LoginMethodSummary
import ink.doa.workbench.core.common.summary.TenantSummary
import ink.doa.workbench.core.identity.LoginDiscoveryRepository
import ink.doa.workbench.core.identity.LoginMethodRepository
import ink.doa.workbench.core.identity.TenantMemberRepository
import ink.doa.workbench.core.identity.UserRepository
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
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking

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
      result.tenantMethods.single().supportedTenants.single().id shouldBe tenant.apiId.value
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
