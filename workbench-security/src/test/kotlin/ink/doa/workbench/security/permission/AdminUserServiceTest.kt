package ink.doa.workbench.security.permission

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.TenantMemberRepository
import ink.doa.workbench.core.identity.UserRepository
import ink.doa.workbench.core.identity.model.UserRecord
import ink.doa.workbench.core.permission.AccessGrantRepository
import ink.doa.workbench.core.permission.AdminScope
import ink.doa.workbench.core.permission.AdminUserCommandRepository
import ink.doa.workbench.core.permission.AdminUserQueryRepository
import ink.doa.workbench.core.permission.AdminUserRecord
import ink.doa.workbench.core.permission.AdminUserStatus
import ink.doa.workbench.security.common.PublicIdResolver
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking

class AdminUserServiceTest :
  StringSpec({
    val adminUserCommands = mockk<AdminUserCommandRepository>()
    val adminUserQueries = mockk<AdminUserQueryRepository>()
    val accessGrants = mockk<AccessGrantRepository>(relaxed = true)
    val userRepository = mockk<UserRepository>()
    val tenantMembers = mockk<TenantMemberRepository>(relaxed = true)
    val publicIds = mockk<PublicIdResolver>()
    val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
    val service =
      AdminUserService(
        AdminUserPersistenceSupport(
          adminUserCommands,
          adminUserQueries,
          accessGrants,
          userRepository,
          tenantMembers,
        ),
        publicIds,
        clock,
      )

    "grantInstanceAdmin creates admin record for resolved user" {
      val user = sampleUser()
      val actorId = UUID.randomUUID()
      val record = sampleAdminRecord(user.id, AdminScope.INSTANCE, tenantId = null)
      coEvery { publicIds.resolveUser(user.apiId.value) } returns user
      coEvery { adminUserCommands.create(any()) } returns record

      val result = runBlocking { service.grantInstanceAdmin(user.apiId.value, actorId) }

      result.id shouldBe record.apiId.value
      result.userId shouldBe user.apiId.value
      result.scope shouldBe AdminScope.INSTANCE
    }

    "revokeAdmin returns false when admin is missing" {
      coEvery { adminUserQueries.findByApiId("adm_missing") } returns null

      runBlocking { service.revokeAdmin("adm_missing") } shouldBe false
    }

    "revokeAdmin delegates to command repository" {
      val record = sampleAdminRecord(UUID.randomUUID(), AdminScope.TENANT, UUID.randomUUID())
      coEvery { adminUserQueries.findByApiId(record.apiId.value) } returns record
      coEvery { adminUserCommands.revoke(record.id, any()) } returns true

      runBlocking { service.revokeAdmin(record.apiId.value) } shouldBe true
      coVerify { adminUserCommands.revoke(record.id, OffsetDateTime.parse("2026-07-04T00:00:00Z")) }
    }

    "listInstanceAdmins maps records with user details" {
      val user = sampleUser()
      val record = sampleAdminRecord(user.id, AdminScope.INSTANCE, tenantId = null)
      coEvery { adminUserQueries.listInstanceAdmins() } returns listOf(record)
      coEvery { userRepository.findById(user.id) } returns user

      val result = runBlocking { service.listInstanceAdmins() }

      result.single().userId shouldBe user.apiId.value
    }

    "grantTenantAdmin creates tenant-scoped admin record" {
      val user = sampleUser()
      val tenantId = UUID.randomUUID()
      val record = sampleAdminRecord(user.id, AdminScope.TENANT, tenantId)
      coEvery { publicIds.resolveUser(user.apiId.value) } returns user
      coEvery { adminUserCommands.create(any()) } returns record

      val result = runBlocking { service.grantTenantAdmin(tenantId, user.apiId.value, null) }

      result.scope shouldBe AdminScope.TENANT
      result.tenantId shouldBe tenantId.toString()
    }

    "listTenantAdmins maps tenant admin records" {
      val user = sampleUser()
      val tenantId = UUID.randomUUID()
      val record = sampleAdminRecord(user.id, AdminScope.TENANT, tenantId)
      coEvery { adminUserQueries.listTenantAdmins(tenantId) } returns listOf(record)
      coEvery { userRepository.findById(user.id) } returns user

      val result = runBlocking { service.listTenantAdmins(tenantId) }

      result.single().tenantId shouldBe tenantId.toString()
    }

    "provisionTenantAdmin creates membership, admin record, and grants" {
      clearMocks(accessGrants, recordedCalls = true)
      val user = sampleUser()
      val tenantId = UUID.randomUUID()
      val record = sampleAdminRecord(user.id, AdminScope.TENANT, tenantId)
      coEvery { userRepository.findById(user.id) } returns user
      coEvery { tenantMembers.create(any()) } returns mockk(relaxed = true)
      coEvery { adminUserCommands.create(any()) } returns record

      val result = runBlocking {
        service.provisionTenantAdmin(tenantId, user.id, UUID.randomUUID())
      }

      result.userId shouldBe user.apiId.value
      coVerify(exactly = 7) { accessGrants.create(any()) }
    }
  })

private fun sampleUser(): UserRecord =
  UserRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("usr"),
    displayName = "Ada",
    primaryEmail = "ada@example.test",
  )

private fun sampleAdminRecord(
  userId: UUID,
  scope: AdminScope,
  tenantId: UUID?,
): AdminUserRecord {
  val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")
  return AdminUserRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("adm"),
    userId = userId,
    scope = scope,
    tenantId = tenantId,
    status = AdminUserStatus.ACTIVE,
    grantedBy = null,
    validFrom = now,
    validTo = null,
    createdAt = now,
    updatedAt = now,
  )
}
