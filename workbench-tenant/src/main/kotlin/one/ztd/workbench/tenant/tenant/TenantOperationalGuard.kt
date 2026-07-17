package one.ztd.workbench.tenant.tenant

import java.util.UUID
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.errors.TenantDestroyingException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.tenant.TenantRepository
import one.ztd.workbench.tenant.model.TenantStatus
import org.springframework.stereotype.Service

@Service
class TenantOperationalGuard(private val tenants: TenantRepository) {
  suspend fun ensureOperational(tenantId: UUID) {
    val tenant =
      tenants.findById(tenantId)
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_TENANT_NOT_FOUND)
    if (tenant.status == TenantStatus.DESTROYING) {
      throw TenantDestroyingException()
    }
  }
}
