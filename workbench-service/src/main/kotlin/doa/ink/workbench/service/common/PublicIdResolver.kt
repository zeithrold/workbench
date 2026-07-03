package doa.ink.workbench.service.common

import doa.ink.workbench.core.common.errors.ResourceNotFoundException
import doa.ink.workbench.core.identity.LoginMethodRepository
import doa.ink.workbench.core.identity.TenantRepository
import doa.ink.workbench.core.identity.UserRepository
import doa.ink.workbench.core.identity.auth.BearerTokenRepository
import doa.ink.workbench.core.identity.model.BearerTokenRecord
import doa.ink.workbench.core.identity.model.LoginMethodDefinitionRecord
import doa.ink.workbench.core.identity.model.TenantRecord
import doa.ink.workbench.core.identity.model.UserRecord
import doa.ink.workbench.core.permission.AccessGrantRecord
import doa.ink.workbench.core.permission.AccessGrantRepository
import doa.ink.workbench.core.permission.AdminUserQueryRepository
import doa.ink.workbench.core.permission.AdminUserRecord
import doa.ink.workbench.core.project.ProjectRepository
import doa.ink.workbench.core.project.model.ProjectRecord
import java.util.UUID
import org.springframework.stereotype.Component

@Component
class PublicIdResolver(
  private val tenants: TenantRepository,
  private val users: UserRepository,
  private val loginMethods: LoginMethodRepository,
  private val bearerTokens: BearerTokenRepository,
  private val adminUserQueries: AdminUserQueryRepository,
  private val accessGrants: AccessGrantRepository,
  private val projects: ProjectRepository,
) {
  suspend fun resolveTenant(publicId: String): TenantRecord =
    tenants.findByApiId(publicId) ?: throw ResourceNotFoundException("Tenant not found.")

  suspend fun resolveTenantForAdmin(publicId: String): TenantRecord =
    tenants.findByApiIdForAdmin(publicId) ?: throw ResourceNotFoundException("Tenant not found.")

  suspend fun resolveUser(publicId: String): UserRecord =
    users.findByApiId(publicId) ?: throw ResourceNotFoundException("User not found.")

  suspend fun resolveLoginMethod(publicId: String): LoginMethodDefinitionRecord =
    loginMethods.findLoginMethodByApiId(publicId)
      ?: throw ResourceNotFoundException("Login method not found.")

  suspend fun resolveBearerToken(publicId: String): BearerTokenRecord =
    bearerTokens.findByApiId(publicId) ?: throw ResourceNotFoundException("Bearer token not found.")

  suspend fun resolveAdminUser(publicId: String): AdminUserRecord =
    adminUserQueries.findByApiId(publicId)
      ?: throw ResourceNotFoundException("Admin user not found.")

  suspend fun resolveAccessGrant(publicId: String): AccessGrantRecord =
    accessGrants.findByApiId(publicId) ?: throw ResourceNotFoundException("Access grant not found.")

  suspend fun resolveProject(tenantId: UUID, publicId: String): ProjectRecord =
    projects.findByApiId(tenantId, publicId)
      ?: throw ResourceNotFoundException("Project not found.")
}
