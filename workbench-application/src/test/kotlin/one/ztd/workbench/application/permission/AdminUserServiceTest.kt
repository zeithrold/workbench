package one.ztd.workbench.application.permission

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import one.ztd.workbench.application.identity.PublicIdResolver
import one.ztd.workbench.identity.TenantMemberRepository
import one.ztd.workbench.identity.UserRepository
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.identity.permission.AccessGrantRepository
import one.ztd.workbench.identity.permission.AdminScope
import one.ztd.workbench.identity.permission.AdminUserCommandRepository
import one.ztd.workbench.identity.permission.AdminUserQueryRepository
import one.ztd.workbench.identity.permission.AdminUserRecord
import one.ztd.workbench.identity.permission.AdminUserStatus
import one.ztd.workbench.identity.permission.GrantScope
import one.ztd.workbench.kernel.common.ids.PublicId

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

      runBlocking { service.revokeInstanceAdmin("adm_missing") } shouldBe false
    }

    "revokeAdmin delegates to command repository" {
      val record = sampleAdminRecord(UUID.randomUUID(), AdminScope.INSTANCE, null)
      coEvery { adminUserQueries.findByApiId(record.apiId.value) } returns record
      coEvery { adminUserQueries.listInstanceAdmins() } returns
        listOf(record, sampleAdminRecord(UUID.randomUUID(), AdminScope.INSTANCE, null))
      coEvery { accessGrants.listBySubject(record.userId, GrantScope.INSTANCE, null, null) } returns
        emptyList()
      coEvery { adminUserCommands.revoke(record.id, any()) } returns true

      runBlocking { service.revokeInstanceAdmin(record.apiId.value) } shouldBe true
      coVerify { adminUserCommands.revoke(record.id, OffsetDateTime.parse("2026-07-04T00:00:00Z")) }
    }

    "revokeInstanceAdmin protects the last active administrator" {
      val record = sampleAdminRecord(UUID.randomUUID(), AdminScope.INSTANCE, null)
      coEvery { adminUserQueries.findByApiId(record.apiId.value) } returns record
      coEvery { adminUserQueries.listInstanceAdmins() } returns listOf(record)

      shouldThrow<one.ztd.workbench.kernel.common.errors.ResourceConflictException> {
        runBlocking { service.revokeInstanceAdmin(record.apiId.value) }
      }
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

    "grantTenantAdmin restores existing membership and provisions permission binding" {
      val user = sampleUser()
      val tenantId = UUID.randomUUID()
      val member = mockk<one.ztd.workbench.identity.model.TenantMemberRecord>()
      val memberId = UUID.randomUUID()
      val bootstrap = mockk<PermissionBootstrapService>()
      val record = sampleAdminRecord(user.id, AdminScope.TENANT, tenantId)
      coEvery { publicIds.resolveUser(user.apiId.value) } returns user
      every { member.id } returns memberId
      every { member.status } returns one.ztd.workbench.identity.model.TenantMemberStatus.SUSPENDED
      coEvery { tenantMembers.findByTenantAndUser(tenantId, user.id) } returns member
      coEvery { tenantMembers.updateStatus(any(), any(), any()) } returns member
      coEvery { adminUserCommands.create(any()) } returns record
      coEvery { bootstrap.provisionTenantAdmin(tenantId, user.id, null) } returns Unit
      val configured =
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
          bootstrap,
        )

      runBlocking { configured.grantTenantAdmin(tenantId, user.apiId.value, null) }

      coVerify {
        tenantMembers.updateStatus(
          memberId,
          one.ztd.workbench.identity.model.TenantMemberStatus.ACTIVE,
          any(),
        )
      }
      coVerify { bootstrap.provisionTenantAdmin(tenantId, user.id, null) }
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

    "revokeTenantAdmin removes binding grants and administrator identity" {
      val tenantId = UUID.randomUUID()
      val record = sampleAdminRecord(UUID.randomUUID(), AdminScope.TENANT, tenantId)
      val other = sampleAdminRecord(UUID.randomUUID(), AdminScope.TENANT, tenantId)
      val grant = mockk<one.ztd.workbench.identity.permission.AccessGrantRecord>()
      val bootstrap = mockk<PermissionBootstrapService>()
      every { grant.id } returns UUID.randomUUID()
      every { grant.validTo } returns null
      coEvery { adminUserQueries.findByApiId(record.apiId.value) } returns record
      coEvery { adminUserQueries.listTenantAdmins(tenantId) } returns listOf(record, other)
      coEvery { bootstrap.revokeTenantAdmin(tenantId, record.userId) } returns true
      coEvery {
        accessGrants.listBySubject(record.userId, GrantScope.TENANT, tenantId, null)
      } returns listOf(grant)
      coEvery { accessGrants.expire(grant.id, any()) } returns true
      coEvery { adminUserCommands.revoke(record.id, any()) } returns true
      val configured =
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
          bootstrap,
        )

      runBlocking { configured.revokeTenantAdmin(tenantId, record.apiId.value) } shouldBe true
      coVerify { bootstrap.revokeTenantAdmin(tenantId, record.userId) }
      coVerify { accessGrants.expire(grant.id, any()) }
    }

    "revokeTenantAdmin protects the last active tenant administrator" {
      val tenantId = UUID.randomUUID()
      val record = sampleAdminRecord(UUID.randomUUID(), AdminScope.TENANT, tenantId)
      coEvery { adminUserQueries.findByApiId(record.apiId.value) } returns record
      coEvery { adminUserQueries.listTenantAdmins(tenantId) } returns listOf(record)

      shouldThrow<one.ztd.workbench.kernel.common.errors.ResourceConflictException> {
        runBlocking { service.revokeTenantAdmin(tenantId, record.apiId.value) }
      }
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
