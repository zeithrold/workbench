package doa.ink.workbench.service.common

import doa.ink.workbench.core.common.errors.ResourceNotFoundException
import doa.ink.workbench.core.identity.LoginAccountRepository
import doa.ink.workbench.core.identity.TenantRepository
import doa.ink.workbench.core.identity.UserRepository
import doa.ink.workbench.core.identity.auth.BearerTokenRepository
import doa.ink.workbench.core.identity.model.BearerTokenRecord
import doa.ink.workbench.core.identity.model.LoginMethodDefinitionRecord
import doa.ink.workbench.core.identity.model.TenantRecord
import doa.ink.workbench.core.identity.model.UserRecord
import doa.ink.workbench.core.permission.PermissionPolicyRecord
import doa.ink.workbench.core.permission.PermissionPolicyRepository
import doa.ink.workbench.core.permission.RoleAssignmentRecord
import doa.ink.workbench.core.permission.RoleAssignmentRepository
import doa.ink.workbench.core.permission.RoleRecord
import doa.ink.workbench.core.permission.RoleRepository
import doa.ink.workbench.core.project.ProjectRepository
import doa.ink.workbench.core.project.model.ProjectRecord
import java.util.UUID
import org.springframework.stereotype.Component

@Component
class PublicIdResolver(
  private val tenants: TenantRepository,
  private val users: UserRepository,
  private val loginAccounts: LoginAccountRepository,
  private val bearerTokens: BearerTokenRepository,
  private val roles: RoleRepository,
  private val policies: PermissionPolicyRepository,
  private val assignments: RoleAssignmentRepository,
  private val projects: ProjectRepository,
) {
  suspend fun resolveTenant(publicId: String): TenantRecord =
    tenants.findByApiId(publicId) ?: throw ResourceNotFoundException("Tenant not found.")

  suspend fun resolveUser(publicId: String): UserRecord =
    users.findByApiId(publicId) ?: throw ResourceNotFoundException("User not found.")

  suspend fun resolveLoginMethod(publicId: String): LoginMethodDefinitionRecord =
    loginAccounts.findLoginMethodByApiId(publicId)
      ?: throw ResourceNotFoundException("Login method not found.")

  suspend fun resolveBearerToken(publicId: String): BearerTokenRecord =
    bearerTokens.findByApiId(publicId) ?: throw ResourceNotFoundException("Bearer token not found.")

  suspend fun resolveRole(tenantId: UUID, publicId: String): RoleRecord =
    roles.findByApiId(tenantId, publicId) ?: throw ResourceNotFoundException("Role not found.")

  suspend fun resolvePolicy(tenantId: UUID, publicId: String): PermissionPolicyRecord =
    policies.findByApiId(tenantId, publicId)
      ?: throw ResourceNotFoundException("Permission policy not found.")

  suspend fun resolveAssignment(tenantId: UUID, publicId: String): RoleAssignmentRecord =
    assignments.findByApiId(tenantId, publicId)
      ?: throw ResourceNotFoundException("Role assignment not found.")

  suspend fun resolveProject(tenantId: UUID, publicId: String): ProjectRecord =
    projects.findByApiId(tenantId, publicId)
      ?: throw ResourceNotFoundException("Project not found.")
}
