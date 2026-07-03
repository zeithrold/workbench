package doa.ink.workbench.service.instance.support

import doa.ink.workbench.core.identity.LoginAccountRepository
import doa.ink.workbench.core.identity.UserRepository
import doa.ink.workbench.core.identity.auth.BearerTokenRepository
import doa.ink.workbench.core.permission.PermissionPolicyRepository
import doa.ink.workbench.core.permission.RoleAssignmentRepository
import doa.ink.workbench.core.permission.RoleRepository
import doa.ink.workbench.core.project.ProjectRepository
import java.time.OffsetDateTime
import java.util.UUID

class UnusedPublicIdResolverDependencies(val loginAccounts: LoginAccountRepository) {
  val users: UserRepository =
    object : UserRepository {
      override suspend fun create(
        command: doa.ink.workbench.core.identity.model.CreateUserCommand
      ) = error("unused")

      override suspend fun findById(id: UUID) = null

      override suspend fun findByApiId(apiId: String) = null

      override suspend fun findByPrimaryEmail(primaryEmail: String) = null

      override suspend fun existsSystemUser(): Boolean = false
    }

  val bearerTokens: BearerTokenRepository =
    object : BearerTokenRepository {
      override suspend fun create(
        command: doa.ink.workbench.core.identity.model.CreateBearerTokenCommand
      ) = error("unused")

      override suspend fun findById(id: UUID) = null

      override suspend fun findByApiId(apiId: String) = null

      override suspend fun findActiveByHash(tokenHash: String, now: OffsetDateTime) = null

      override suspend fun revoke(id: UUID, revokedAt: OffsetDateTime) = false

      override suspend fun touch(id: UUID, usedAt: OffsetDateTime) = false
    }

  val roles: RoleRepository =
    object : RoleRepository {
      override suspend fun create(command: doa.ink.workbench.core.permission.CreateRoleCommand) =
        error("unused")

      override suspend fun findById(id: UUID) = null

      override suspend fun findByApiId(tenantId: UUID?, apiId: String) = null

      override suspend fun findByCode(tenantId: UUID?, code: String) = null

      override suspend fun list(tenantId: UUID?) =
        emptyList<doa.ink.workbench.core.permission.RoleRecord>()
    }

  val policies: PermissionPolicyRepository =
    object : PermissionPolicyRepository {
      override suspend fun create(
        command: doa.ink.workbench.core.permission.CreatePermissionPolicyCommand
      ) = error("unused")

      override suspend fun listByTenant(tenantId: UUID) =
        emptyList<doa.ink.workbench.core.permission.PermissionPolicyRecord>()

      override suspend fun findByApiId(tenantId: UUID, apiId: String) = null

      override suspend fun listActiveByRoles(
        tenantId: UUID,
        roleIds: Collection<UUID>,
        at: OffsetDateTime,
      ) = emptyList<doa.ink.workbench.core.permission.PermissionPolicyRecord>()

      override suspend fun expire(id: UUID, validTo: OffsetDateTime) = false
    }

  val assignments: RoleAssignmentRepository =
    object : RoleAssignmentRepository {
      override suspend fun assign(command: doa.ink.workbench.core.permission.AssignRoleCommand) =
        error("unused")

      override suspend fun listByTenant(tenantId: UUID) =
        emptyList<doa.ink.workbench.core.permission.RoleAssignmentRecord>()

      override suspend fun findByApiId(tenantId: UUID, apiId: String) = null

      override suspend fun listActiveByUser(
        tenantId: UUID,
        userId: UUID,
        projectId: UUID?,
        at: OffsetDateTime,
      ) = emptyList<doa.ink.workbench.core.permission.RoleAssignmentRecord>()

      override suspend fun revoke(id: UUID, revokedAt: OffsetDateTime) = false
    }

  val projects: ProjectRepository =
    object : ProjectRepository {
      override suspend fun create(
        command: doa.ink.workbench.core.project.model.CreateProjectCommand
      ) = error("unused")

      override suspend fun findByApiId(tenantId: UUID, apiId: String) = null

      override suspend fun findById(tenantId: UUID, id: UUID) = null

      override suspend fun list(tenantId: UUID, identifier: String?) =
        emptyList<doa.ink.workbench.core.project.model.ProjectRecord>()

      override suspend fun update(
        command: doa.ink.workbench.core.project.model.UpdateProjectCommand
      ) = error("unused")

      override suspend fun delete(tenantId: UUID, projectId: UUID) = false
    }
}
