package one.ztd.workbench.tenant.tenant

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.OffsetDateTime
import java.util.UUID
import one.ztd.workbench.kernel.common.errors.TenantDestroyingException
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.tenant.TenantRepository
import one.ztd.workbench.tenant.model.TenantRecord
import one.ztd.workbench.tenant.model.TenantStatus

class TenantOperationalGuardTest :
  StringSpec({
    val tenantId = UUID.randomUUID()
    val tenants = mockk<TenantRepository>()

    "allows operational tenant" {
      coEvery { tenants.findById(tenantId) } returns sampleTenant(TenantStatus.ACTIVE)
      TenantOperationalGuard(tenants).ensureOperational(tenantId) shouldBe Unit
    }

    "rejects destroying tenant" {
      coEvery { tenants.findById(tenantId) } returns sampleTenant(TenantStatus.DESTROYING)
      shouldThrow<TenantDestroyingException> {
        TenantOperationalGuard(tenants).ensureOperational(tenantId)
      }
    }
  })

private fun sampleTenant(status: TenantStatus) =
  TenantRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("ten"),
    slug = "acme",
    name = "Acme",
    status = status,
    createdAt = OffsetDateTime.parse("2026-07-03T00:00:00Z"),
    updatedAt = OffsetDateTime.parse("2026-07-03T00:00:00Z"),
  )
