package one.ztd.workbench.identity.common

import one.ztd.workbench.identity.model.BearerTokenRecord
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.tenant.TenantRepository
import one.ztd.workbench.tenant.model.TenantRecord
import org.springframework.stereotype.Component

@Component
class IdentityPublicIdResolver(
  private val tenants: TenantRepository,
  private val bearerTokens: one.ztd.workbench.identity.auth.BearerTokenRepository,
) {
  suspend fun resolveTenant(publicId: String): TenantRecord =
    tenants.findByApiId(publicId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_TENANT_NOT_FOUND)

  suspend fun resolveBearerToken(publicId: String): BearerTokenRecord =
    bearerTokens.findByApiId(publicId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_BEARER_TOKEN_NOT_FOUND)
}
