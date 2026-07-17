package one.ztd.workbench.tenant.instance

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
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.tenant.TenantRepository
import one.ztd.workbench.tenant.model.FinalizeTenantDestroyCommand
import one.ztd.workbench.tenant.model.TenantRecord
import one.ztd.workbench.tenant.model.TenantStatus
import one.ztd.workbench.tenant.tenant.TenantDestructionRepository

class TenantDestructionServiceTest :
  StringSpec({
    val tenants = mockk<TenantRepository>()
    val destruction = mockk<TenantDestructionRepository>(relaxed = true)
    val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
    val service = TenantDestructionService(tenants, destruction, clock)
    val deletedBy = UUID.randomUUID()

    "execute returns false when tenant is missing" {
      val tenantId = UUID.randomUUID()
      coEvery { tenants.findByIdForDestruction(tenantId) } returns null

      runBlocking { service.execute(tenantId, deletedBy, "cleanup") } shouldBe false
    }

    "execute returns false when tenant is not destroying" {
      val tenant = sampleTenant(TenantStatus.ACTIVE)
      coEvery { tenants.findByIdForDestruction(tenant.id) } returns tenant

      runBlocking { service.execute(tenant.id, deletedBy, null) } shouldBe false
    }

    "execute runs destruction steps and finalizes tenant" {
      val tenant = sampleTenant(TenantStatus.DESTROYING)
      coEvery { tenants.findByIdForDestruction(tenant.id) } returns tenant
      coEvery {
        tenants.finalizeDestroy(
          FinalizeTenantDestroyCommand(
            tenantId = tenant.id,
            deletedBy = deletedBy,
            deleteReason = "cleanup",
          )
        )
      } returns true

      runBlocking { service.execute(tenant.id, deletedBy, "cleanup") } shouldBe true

      val expectedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z")
      coVerify { destruction.revokeSessionsByActiveTenant(tenant.id, expectedAt) }
      coVerify { destruction.revokeBearerTokensByTenant(tenant.id, expectedAt) }
      coVerify { destruction.revokeAdminUsersByTenant(tenant.id, expectedAt) }
      coVerify { destruction.expireAccessGrantsByTenant(tenant.id, expectedAt) }
      coVerify { destruction.cancelPendingInvitationsByTenant(tenant.id, expectedAt) }
      coVerify {
        destruction.softDeleteTenantScopedData(tenant.id, expectedAt, deletedBy, "cleanup")
      }
    }

    "execute returns false when finalize reports already deleted" {
      val tenant = sampleTenant(TenantStatus.DESTROYING)
      coEvery { tenants.findByIdForDestruction(tenant.id) } returns tenant
      coEvery {
        tenants.finalizeDestroy(
          FinalizeTenantDestroyCommand(
            tenantId = tenant.id,
            deletedBy = deletedBy,
            deleteReason = null,
          )
        )
      } returns false

      runBlocking { service.execute(tenant.id, deletedBy, null) } shouldBe false
    }
  })

private fun sampleTenant(status: TenantStatus): TenantRecord =
  TenantRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("ten"),
    slug = "acme",
    name = "Acme",
    status = status,
    createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
    updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
  )
