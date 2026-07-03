package ink.doa.workbench.tenant.tenant

import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.TenantDestroyingException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.identity.TenantRepository
import ink.doa.workbench.core.identity.model.TenantStatus
import java.util.UUID
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
