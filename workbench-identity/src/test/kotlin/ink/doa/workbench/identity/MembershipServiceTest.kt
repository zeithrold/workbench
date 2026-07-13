package ink.doa.workbench.identity

import ink.doa.workbench.identity.model.TenantMemberRecord
import ink.doa.workbench.identity.model.TenantMemberStatus
import ink.doa.workbench.identity.permission.AdminUserQueryRepository
import ink.doa.workbench.kernel.common.ids.PublicId
import ink.doa.workbench.tenant.TenantRepository
import ink.doa.workbench.tenant.model.TenantRecord
import ink.doa.workbench.tenant.model.TenantStatus
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

class MembershipServiceTest :
  StringSpec({
    val tenantMembers = mockk<TenantMemberRepository>()
    val tenants = mockk<TenantRepository>()
    val adminUserQueries = mockk<AdminUserQueryRepository>()
    val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
    val service = MembershipService(tenantMembers, tenants, adminUserQueries, clock)
    val userId = UUID.randomUUID()

    "listActiveMemberships returns active memberships with tenant admin flag" {
      val tenant = sampleTenant()
      val membership = sampleMembership(tenant.id, userId, TenantMemberStatus.ACTIVE)
      coEvery { tenantMembers.listByUser(userId) } returns
        listOf(membership, sampleMembership(UUID.randomUUID(), userId, TenantMemberStatus.INVITED))
      coEvery { tenants.findByIds(listOf(tenant.id)) } returns listOf(tenant)
      coEvery {
        adminUserQueries.isActiveTenantAdmin(
          tenant.id,
          userId,
          OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        )
      } returns true

      val result = runBlocking { service.listActiveMemberships(userId) }

      result.single().id shouldBe membership.apiId.value
      result.single().tenant.name shouldBe tenant.name
      result.single().isTenantAdmin shouldBe true
    }

    "listActiveMemberships skips memberships when tenant is missing" {
      val missingTenantId = UUID.randomUUID()
      val membership = sampleMembership(missingTenantId, userId, TenantMemberStatus.ACTIVE)
      coEvery { tenantMembers.listByUser(userId) } returns listOf(membership)
      coEvery { tenants.findByIds(listOf(missingTenantId)) } returns emptyList()

      runBlocking { service.listActiveMemberships(userId) }.shouldBeEmpty()
    }
  })

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

private fun sampleMembership(
  tenantId: UUID,
  userId: UUID,
  status: TenantMemberStatus,
): TenantMemberRecord {
  val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")
  return TenantMemberRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("mbr"),
    tenantId = tenantId,
    userId = userId,
    status = status,
    joinedAt = now,
    invitedBy = null,
    createdAt = now,
    updatedAt = now,
  )
}
