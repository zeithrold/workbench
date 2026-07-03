package doa.ink.workbench.service.tenant

import doa.ink.workbench.core.common.errors.TenantDestroyingException
import doa.ink.workbench.core.common.ids.PublicId
import doa.ink.workbench.core.identity.TenantRepository
import doa.ink.workbench.core.identity.model.TenantRecord
import doa.ink.workbench.core.identity.model.TenantStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.OffsetDateTime
import java.util.UUID

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
