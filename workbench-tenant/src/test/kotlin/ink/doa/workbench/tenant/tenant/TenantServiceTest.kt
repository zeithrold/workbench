package ink.doa.workbench.tenant.tenant

import ink.doa.workbench.core.common.errors.ResourceConflictException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.TenantRepository
import ink.doa.workbench.core.identity.model.CreateTenantCommand
import ink.doa.workbench.core.identity.model.TenantRecord
import ink.doa.workbench.core.identity.model.TenantStatus
import ink.doa.workbench.core.identity.model.UpdateTenantCommand
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking

class TenantServiceTest :
  StringSpec({
    val tenants = mockk<TenantRepository>()
    val service = TenantService(tenants)

    "getForAdmin returns tenant when found" {
      val tenant = sampleTenant(TenantStatus.ACTIVE)
      coEvery { tenants.findByApiIdForAdmin(tenant.apiId.value) } returns tenant

      runBlocking { service.getForAdmin(tenant.apiId.value) } shouldBe tenant
    }

    "getForAdmin throws when tenant is missing" {
      coEvery { tenants.findByApiIdForAdmin("ten_missing") } returns null

      shouldThrow<ResourceNotFoundException> {
        runBlocking { service.getForAdmin("ten_missing") }
      }
    }

    "update rejects destroying tenant" {
      val tenant = sampleTenant(TenantStatus.DESTROYING)
      coEvery { tenants.findByApiIdForAdmin(tenant.apiId.value) } returns tenant

      shouldThrow<ResourceConflictException> {
        runBlocking {
          service.update(
            tenant.apiId.value,
            name = "New Name",
            slug = null,
            timezone = null,
            locale = null,
          )
        }
      }
    }

    "update delegates to repository for active tenant" {
      val tenant = sampleTenant(TenantStatus.ACTIVE)
      val updated = tenant.copy(name = "Renamed")
      coEvery { tenants.findByApiIdForAdmin(tenant.apiId.value) } returns tenant
      coEvery {
        tenants.update(
          UpdateTenantCommand(
            tenantId = tenant.id,
            name = "Renamed",
            slug = null,
            timezone = null,
            locale = null,
          )
        )
      } returns updated

      runBlocking {
        service.update(
          tenant.apiId.value,
          name = "Renamed",
          slug = null,
          timezone = null,
          locale = null,
        )
      } shouldBe updated
    }

    "create delegates to repository" {
      val command =
        CreateTenantCommand(
          slug = "acme",
          name = "Acme",
          timezone = "UTC",
          locale = "en-US",
        )
      val created = sampleTenant(TenantStatus.ACTIVE)
      coEvery { tenants.create(command) } returns created

      runBlocking { service.create(command) } shouldBe created
    }

    "markDestroying delegates to repository" {
      val tenant = sampleTenant(TenantStatus.DESTROYING)
      coEvery { tenants.markDestroying(tenant.id) } returns tenant

      runBlocking { service.markDestroying(tenant.id) } shouldBe tenant
    }

    "restoreStatus delegates to repository" {
      val tenant = sampleTenant(TenantStatus.ACTIVE)
      coEvery {
        tenants.update(UpdateTenantCommand(tenantId = tenant.id, status = TenantStatus.ACTIVE))
      } returns tenant

      runBlocking { service.restoreStatus(tenant.id, TenantStatus.ACTIVE) } shouldBe tenant
      coVerify {
        tenants.update(UpdateTenantCommand(tenantId = tenant.id, status = TenantStatus.ACTIVE))
      }
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
