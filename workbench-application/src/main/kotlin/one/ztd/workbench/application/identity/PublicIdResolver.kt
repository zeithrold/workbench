package one.ztd.workbench.application.identity

import java.util.UUID
import one.ztd.workbench.agile.project.ProjectRepository
import one.ztd.workbench.agile.project.model.ProjectRecord
import one.ztd.workbench.identity.common.PublicIdIdentitySupport
import one.ztd.workbench.identity.common.PublicIdPermissionSupport
import one.ztd.workbench.identity.model.BearerTokenRecord
import one.ztd.workbench.identity.model.LoginMethodDefinitionRecord
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.identity.permission.AccessGrantRecord
import one.ztd.workbench.identity.permission.AdminUserRecord
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.tenant.model.TenantRecord
import org.springframework.stereotype.Component

@Component
class PublicIdResolver(
  private val identity: PublicIdIdentitySupport,
  private val permission: PublicIdPermissionSupport,
  private val projects: ProjectRepository,
) {
  suspend fun resolveTenant(publicId: String): TenantRecord =
    identity.tenants.findByApiId(publicId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_TENANT_NOT_FOUND)

  suspend fun resolveTenantForAdmin(publicId: String): TenantRecord =
    identity.tenants.findByApiIdForAdmin(publicId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_TENANT_NOT_FOUND)

  suspend fun resolveUser(publicId: String): UserRecord =
    identity.users.findByApiId(publicId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND)

  suspend fun resolveLoginMethod(publicId: String): LoginMethodDefinitionRecord =
    identity.loginMethods.findLoginMethodByApiId(publicId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_LOGIN_METHOD_NOT_FOUND)

  suspend fun resolveBearerToken(publicId: String): BearerTokenRecord =
    identity.bearerTokens.findByApiId(publicId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_BEARER_TOKEN_NOT_FOUND)

  suspend fun resolveAdminUser(publicId: String): AdminUserRecord =
    permission.adminUserQueries.findByApiId(publicId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_ADMIN_USER_NOT_FOUND)

  suspend fun resolveAccessGrant(publicId: String): AccessGrantRecord =
    permission.accessGrants.findByApiId(publicId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_ACCESS_GRANT_NOT_FOUND)

  suspend fun resolveProject(tenantId: UUID, publicId: String): ProjectRecord =
    projects.findByApiId(tenantId, publicId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PROJECT_NOT_FOUND)
}
