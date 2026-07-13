package ink.doa.workbench.application.identity

import ink.doa.workbench.agile.project.ProjectRepository
import ink.doa.workbench.agile.project.model.ProjectRecord
import ink.doa.workbench.identity.common.PublicIdIdentitySupport
import ink.doa.workbench.identity.common.PublicIdPermissionSupport
import ink.doa.workbench.identity.model.BearerTokenRecord
import ink.doa.workbench.identity.model.LoginMethodDefinitionRecord
import ink.doa.workbench.identity.model.UserRecord
import ink.doa.workbench.identity.permission.AccessGrantRecord
import ink.doa.workbench.identity.permission.AdminUserRecord
import ink.doa.workbench.kernel.common.errors.ResourceNotFoundException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import ink.doa.workbench.tenant.model.TenantRecord
import java.util.UUID
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
