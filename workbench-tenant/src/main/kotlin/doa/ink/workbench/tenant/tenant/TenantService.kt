package doa.ink.workbench.tenant.tenant

import doa.ink.workbench.core.common.errors.ResourceConflictException
import doa.ink.workbench.core.common.errors.ResourceNotFoundException
import doa.ink.workbench.core.common.errors.WorkbenchErrorCode
import doa.ink.workbench.core.identity.TenantRepository
import doa.ink.workbench.core.identity.model.CreateTenantCommand
import doa.ink.workbench.core.identity.model.TenantRecord
import doa.ink.workbench.core.identity.model.TenantStatus
import doa.ink.workbench.core.identity.model.UpdateTenantCommand
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

  suspend fun restoreStatus(tenantId: UUID, status: TenantStatus): TenantRecord =
    tenants.update(UpdateTenantCommand(tenantId = tenantId, status = status))
}
