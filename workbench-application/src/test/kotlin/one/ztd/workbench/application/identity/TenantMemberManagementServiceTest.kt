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
import one.ztd.workbench.application.permission.AdminUserService
import one.ztd.workbench.identity.TenantMemberRepository
import one.ztd.workbench.identity.UserRepository
import one.ztd.workbench.identity.model.TenantMemberRecord
import one.ztd.workbench.identity.model.TenantMemberStatus
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.identity.permission.AdminScope
import one.ztd.workbench.identity.permission.AdminUserQueryRepository
import one.ztd.workbench.identity.permission.AdminUserRecord
import one.ztd.workbench.identity.permission.AdminUserStatus
import one.ztd.workbench.kernel.common.errors.ResourceConflictException
import one.ztd.workbench.kernel.common.ids.PublicId

class TenantMemberManagementServiceTest :
  StringSpec({
    val members = mockk<TenantMemberRepository>()
    val users = mockk<UserRepository>()
    val adminQueries = mockk<AdminUserQueryRepository>()
    val admins = mockk<AdminUserService>()
    val service =
      TenantMemberManagementService(
        members,
        users,
        adminQueries,
        admins,
        Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC),
      )

    "protects the last effective tenant administrator" {
      val member = member()
      val admin = admin(member)
      coEvery { members.findByApiId(member.tenantId, member.apiId.value) } returns member
      coEvery { adminQueries.findActiveTenantAdmin(member.tenantId, member.userId, any()) } returns
        admin
      coEvery { adminQueries.listTenantAdmins(member.tenantId) } returns listOf(admin)

      shouldThrow<ResourceConflictException> {
        runBlocking { service.suspendMember(member.tenantId, member.apiId.value) }
      }
      coVerify(exactly = 0) { members.updateStatus(any(), any(), any()) }
    }

    "lists members and restores a suspended member" {
      val member = member().copy(status = TenantMemberStatus.SUSPENDED)
      val user = user(member.userId)
      coEvery { members.listByTenant(member.tenantId) } returns listOf(member)
      coEvery { users.findById(member.userId) } returns user
      coEvery { adminQueries.isActiveTenantAdmin(member.tenantId, member.userId, any()) } returns
        false
      coEvery { members.findByApiId(member.tenantId, member.apiId.value) } returns member
      coEvery { adminQueries.findActiveTenantAdmin(member.tenantId, member.userId, any()) } returns
        null
      coEvery { members.updateStatus(member.id, TenantMemberStatus.ACTIVE, any()) } returns
        member.copy(status = TenantMemberStatus.ACTIVE)

      runBlocking { service.list(member.tenantId).single().user.id } shouldBe user.apiId
      runBlocking { service.restoreMember(member.tenantId, member.apiId.value).status } shouldBe
        TenantMemberStatus.ACTIVE
    }

    "revokes a non-final administrator before removing membership" {
      val member = member()
      val admin = admin(member)
      val otherAdmin = admin(member.copy(userId = UUID.randomUUID()))
      val removed = member.copy(status = TenantMemberStatus.REMOVED)
      coEvery { members.findByApiId(member.tenantId, member.apiId.value) } returns member
      coEvery { adminQueries.findActiveTenantAdmin(member.tenantId, member.userId, any()) } returns
        admin
      coEvery { adminQueries.listTenantAdmins(member.tenantId) } returns listOf(admin, otherAdmin)
      coEvery { admins.revokeTenantAdmin(member.tenantId, admin.apiId.value) } returns true
      coEvery { members.updateStatus(member.id, TenantMemberStatus.REMOVED, any()) } returns removed
      coEvery { users.findById(member.userId) } returns user(member.userId)
      coEvery { adminQueries.isActiveTenantAdmin(member.tenantId, member.userId, any()) } returns
        false

      runBlocking { service.removeMember(member.tenantId, member.apiId.value).status } shouldBe
        TenantMemberStatus.REMOVED
      coVerify { admins.revokeTenantAdmin(member.tenantId, admin.apiId.value) }
    }
  })

private fun user(id: UUID): UserRecord =
  UserRecord(
    id = id,
    apiId = PublicId.new("usr"),
    displayName = "Ada",
    primaryEmail = "ada@example.test",
  )

private fun member(): TenantMemberRecord {
  val now = OffsetDateTime.parse("2026-07-15T00:00:00Z")
  return TenantMemberRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("tmb"),
    tenantId = UUID.randomUUID(),
    userId = UUID.randomUUID(),
    status = TenantMemberStatus.ACTIVE,
    joinedAt = now,
    invitedBy = null,
    createdAt = now,
    updatedAt = now,
  )
}

private fun admin(member: TenantMemberRecord): AdminUserRecord {
  val now = OffsetDateTime.parse("2026-07-15T00:00:00Z")
  return AdminUserRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("adm"),
    userId = member.userId,
    scope = AdminScope.TENANT,
    tenantId = member.tenantId,
    status = AdminUserStatus.ACTIVE,
    grantedBy = null,
    validFrom = now,
    validTo = null,
    createdAt = now,
    updatedAt = now,
  )
}
