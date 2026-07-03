package doa.ink.workbench.service.common

import doa.ink.workbench.core.common.context.TenantRequestContext
import doa.ink.workbench.core.common.errors.PermissionDeniedException
import java.util.UUID

interface TenantOwned {
  val tenantId: UUID
}

object TenantAccessGuard {
  fun ensureAccessible(context: TenantRequestContext, entity: TenantOwned) {
    if (entity.tenantId != context.tenant.id) {
      throw PermissionDeniedException("Resource is not accessible in the current tenant context.")
    }
  }
}
