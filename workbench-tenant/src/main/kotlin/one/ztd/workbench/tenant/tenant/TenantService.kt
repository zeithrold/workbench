package one.ztd.workbench.tenant.tenant

import java.util.UUID
import one.ztd.workbench.kernel.common.errors.ResourceConflictException
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.tenant.TenantRepository
import one.ztd.workbench.tenant.model.CreateTenantCommand
import one.ztd.workbench.tenant.model.TenantRecord
import one.ztd.workbench.tenant.model.TenantStatus
import one.ztd.workbench.tenant.model.UpdateTenantCommand
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
    payload: one.ztd.workbench.tenant.tenant.events.TenantDestroyRequestedEvent,
  ): TenantRecord = tenants.requestDestroy(tenantId, tenantApiId, payload)

  suspend fun restoreStatus(tenantId: UUID, status: TenantStatus): TenantRecord =
    tenants.update(UpdateTenantCommand(tenantId = tenantId, status = status))
}
