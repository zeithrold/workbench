package doa.ink.workbench.tenant.tenant

import doa.ink.workbench.core.common.errors.ResourceNotFoundException
import doa.ink.workbench.core.common.errors.TenantDestroyingException
import doa.ink.workbench.core.identity.TenantRepository
import doa.ink.workbench.core.identity.model.TenantStatus
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class TenantOperationalGuard(private val tenants: TenantRepository) {
  suspend fun ensureOperational(tenantId: UUID) {
    val tenant = tenants.findById(tenantId) ?: throw ResourceNotFoundException("Tenant not found.")
    if (tenant.status == TenantStatus.DESTROYING) {
      throw TenantDestroyingException("Tenant is being destroyed.")
    }
  }
}
