package ink.doa.workbench.service.instance.support

import ink.doa.workbench.core.identity.LoginMethodRepository
import ink.doa.workbench.core.identity.UserRepository
import ink.doa.workbench.core.identity.auth.BearerTokenRepository
import ink.doa.workbench.core.permission.AccessGrantRepository
import ink.doa.workbench.core.permission.AdminUserCommandRepository
import ink.doa.workbench.core.permission.AdminUserQueryRepository
import ink.doa.workbench.core.project.ProjectRepository
import java.time.OffsetDateTime
import java.util.UUID

class UnusedPublicIdResolverDependencies(val loginMethods: LoginMethodRepository) {
  val users: UserRepository =
    object : UserRepository {
      override suspend fun create(
        command: ink.doa.workbench.core.identity.model.CreateUserCommand
      ) = error("unused")

      override suspend fun findById(id: UUID) = null

      override suspend fun findByApiId(apiId: String) = null

      override suspend fun findByPrimaryEmail(primaryEmail: String) = null
    }

  val bearerTokens: BearerTokenRepository =
    object : BearerTokenRepository {
      override suspend fun create(
        command: ink.doa.workbench.core.identity.model.CreateBearerTokenCommand
      ) = error("unused")

      override suspend fun findById(id: UUID) = null

      override suspend fun findByApiId(apiId: String) = null

      override suspend fun findActiveByHash(tokenHash: String, now: OffsetDateTime) = null

      override suspend fun revoke(id: UUID, revokedAt: OffsetDateTime) = false

      override suspend fun revokeByTenant(tenantId: UUID, revokedAt: OffsetDateTime) = 0

      override suspend fun touch(id: UUID, usedAt: OffsetDateTime) = false
    }

  val adminUserQueries: AdminUserQueryRepository =
    object : AdminUserQueryRepository {
      override suspend fun findById(id: UUID) = null

      override suspend fun findByApiId(apiId: String) = null

      override suspend fun findActiveInstanceAdmin(userId: UUID, at: OffsetDateTime) = null

      override suspend fun findActiveTenantAdmin(
        tenantId: UUID,
        userId: UUID,
        at: OffsetDateTime,
      ) = null

      override suspend fun existsActiveInstanceAdmin() = false

      override suspend fun isActiveInstanceAdmin(userId: UUID, at: OffsetDateTime) = false

      override suspend fun isActiveTenantAdmin(tenantId: UUID, userId: UUID, at: OffsetDateTime) =
        false

      override suspend fun listByUser(userId: UUID) =
        emptyList<ink.doa.workbench.core.permission.AdminUserRecord>()

      override suspend fun listInstanceAdmins() =
        emptyList<ink.doa.workbench.core.permission.AdminUserRecord>()

      override suspend fun listTenantAdmins(tenantId: UUID) =
        emptyList<ink.doa.workbench.core.permission.AdminUserRecord>()
    }

  val adminUserCommands: AdminUserCommandRepository =
    object : AdminUserCommandRepository {
      override suspend fun create(
        command: ink.doa.workbench.core.permission.CreateAdminUserCommand
      ) = error("unused")

      override suspend fun revoke(id: UUID, revokedAt: OffsetDateTime) = false

      override suspend fun revokeByTenant(tenantId: UUID, revokedAt: OffsetDateTime) = 0
    }

  val accessGrants: AccessGrantRepository =
    object : AccessGrantRepository {
      override suspend fun create(
        command: ink.doa.workbench.core.permission.CreateAccessGrantCommand
      ) = error("unused")

      override suspend fun findById(id: UUID) = null

      override suspend fun findByApiId(apiId: String) = null

      override suspend fun listBySubject(
        subjectUserId: UUID,
        scope: ink.doa.workbench.core.permission.GrantScope?,
        tenantId: UUID?,
        projectId: UUID?,
      ) = emptyList<ink.doa.workbench.core.permission.AccessGrantRecord>()

      override suspend fun listActiveForSubject(
        subjectUserId: UUID,
        scope: ink.doa.workbench.core.permission.GrantScope,
        tenantId: UUID?,
        projectId: UUID?,
        at: OffsetDateTime,
      ) = emptyList<ink.doa.workbench.core.permission.AccessGrantRecord>()

      override suspend fun listByTenant(tenantId: UUID) =
        emptyList<ink.doa.workbench.core.permission.AccessGrantRecord>()

      override suspend fun listInstanceGrants() =
        emptyList<ink.doa.workbench.core.permission.AccessGrantRecord>()

      override suspend fun expire(id: UUID, validTo: OffsetDateTime) = false

      override suspend fun expireByTenant(tenantId: UUID, expiredAt: OffsetDateTime) = 0
    }

  val projects: ProjectRepository =
    object : ProjectRepository {
      override suspend fun create(
        command: ink.doa.workbench.core.project.model.CreateProjectCommand
      ) = error("unused")

      override suspend fun findByApiId(tenantId: UUID, apiId: String) = null

      override suspend fun findById(tenantId: UUID, id: UUID) = null

      override suspend fun list(tenantId: UUID, identifier: String?) =
        emptyList<ink.doa.workbench.core.project.model.ProjectRecord>()

      override suspend fun update(
        command: ink.doa.workbench.core.project.model.UpdateProjectCommand
      ) = error("unused")

      override suspend fun markArchived(
        tenantId: UUID,
        projectId: UUID,
        archivedAt: OffsetDateTime,
        archivedBy: UUID,
      ) = error("unused")

      override suspend fun markActive(tenantId: UUID, projectId: UUID) = error("unused")

      override suspend fun markDestroying(
        tenantId: UUID,
        projectId: UUID,
        deletedBy: UUID,
        deleteReason: String?,
      ) = error("unused")

      override suspend fun requestDestroy(
        request: ink.doa.workbench.core.project.ProjectDestroyRequest
      ) = error("unused")

      override suspend fun finalizeDestroy(
        tenantId: UUID,
        projectId: UUID,
        deletedAt: OffsetDateTime,
        deletedBy: UUID,
        deleteReason: String?,
      ) = false

      override suspend fun updateStatus(
        tenantId: UUID,
        projectId: UUID,
        status: ink.doa.workbench.core.project.model.ProjectStatus,
      ) = false
    }
}
