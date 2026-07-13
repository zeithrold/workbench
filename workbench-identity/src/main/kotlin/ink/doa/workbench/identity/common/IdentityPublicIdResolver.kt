package ink.doa.workbench.identity.common

import ink.doa.workbench.identity.model.BearerTokenRecord
import ink.doa.workbench.kernel.common.errors.ResourceNotFoundException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import ink.doa.workbench.tenant.TenantRepository
import ink.doa.workbench.tenant.model.TenantRecord
import org.springframework.stereotype.Component

@Component
class IdentityPublicIdResolver(
  private val tenants: TenantRepository,
  private val bearerTokens: ink.doa.workbench.identity.auth.BearerTokenRepository,
) {
  suspend fun resolveTenant(publicId: String): TenantRecord =
    tenants.findByApiId(publicId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_TENANT_NOT_FOUND)

  suspend fun resolveBearerToken(publicId: String): BearerTokenRecord =
    bearerTokens.findByApiId(publicId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_BEARER_TOKEN_NOT_FOUND)
}
