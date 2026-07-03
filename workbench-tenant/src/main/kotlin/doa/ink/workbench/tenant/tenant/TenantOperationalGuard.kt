package doa.ink.workbench.tenant.tenant

import doa.ink.workbench.core.common.errors.ResourceNotFoundException
import doa.ink.workbench.core.common.errors.TenantDestroyingException
import doa.ink.workbench.core.common.errors.WorkbenchErrorCode
import doa.ink.workbench.core.identity.TenantRepository
import doa.ink.workbench.core.identity.model.TenantStatus
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
