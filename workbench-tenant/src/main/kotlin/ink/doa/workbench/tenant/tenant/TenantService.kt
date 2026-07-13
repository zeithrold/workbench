package ink.doa.workbench.tenant.tenant

import ink.doa.workbench.kernel.common.errors.ResourceConflictException
import ink.doa.workbench.kernel.common.errors.ResourceNotFoundException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import ink.doa.workbench.tenant.TenantRepository
import ink.doa.workbench.tenant.model.CreateTenantCommand
import ink.doa.workbench.tenant.model.TenantRecord
import ink.doa.workbench.tenant.model.TenantStatus
import ink.doa.workbench.tenant.model.UpdateTenantCommand
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class TenantService(private val tenants: TenantRepository) {
  suspend fun listForAdmin(slug: String? = null): List<TenantRecord> = tenants.listForAdmin(slug)

  suspend fun getForAdmin(tenantPublicId: String): TenantRecord =
    tenants.findByApiIdForAdmin(tenantPublicId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_TENANT_NOT_FOUND)

  suspend fun create(command: CreateTenantCommand): TenantRecord = tenants.create(command)

  suspend fun update(
    tenantPublicId: String,
    name: String?,
    slug: String?,
    timezone: String?,
    locale: String?,
  ): TenantRecord {
    val tenant = getForAdmin(tenantPublicId)
    if (tenant.status == TenantStatus.DESTROYING) {
      throw ResourceConflictException(WorkbenchErrorCode.TENANT_DESTROYING_UPDATE_FORBIDDEN)
    }
    return tenants.update(
      UpdateTenantCommand(
        tenantId = tenant.id,
        name = name,
        slug = slug,
        timezone = timezone,
        locale = locale,
      )
    )
  }

  suspend fun markDestroying(tenantId: UUID): TenantRecord = tenants.markDestroying(tenantId)

  suspend fun requestDestroy(
    tenantId: UUID,
    tenantApiId: String,
    payload: ink.doa.workbench.tenant.tenant.events.TenantDestroyRequestedEvent,
  ): TenantRecord = tenants.requestDestroy(tenantId, tenantApiId, payload)

  suspend fun restoreStatus(tenantId: UUID, status: TenantStatus): TenantRecord =
    tenants.update(UpdateTenantCommand(tenantId = tenantId, status = status))
}
