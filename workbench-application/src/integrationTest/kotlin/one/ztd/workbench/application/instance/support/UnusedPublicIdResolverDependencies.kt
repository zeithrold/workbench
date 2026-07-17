package one.ztd.workbench.application.instance.support

import java.time.OffsetDateTime
import java.util.UUID
import one.ztd.workbench.agile.project.ProjectRepository
import one.ztd.workbench.identity.LoginMethodRepository
import one.ztd.workbench.identity.UserRepository
import one.ztd.workbench.identity.auth.BearerTokenRepository
import one.ztd.workbench.identity.permission.AccessGrantRepository
import one.ztd.workbench.identity.permission.AdminUserCommandRepository
import one.ztd.workbench.identity.permission.AdminUserQueryRepository

class UnusedPublicIdResolverDependencies(val loginMethods: LoginMethodRepository) {
  val users: UserRepository =
    object : UserRepository {
      override suspend fun create(command: one.ztd.workbench.identity.model.CreateUserCommand) =
        error("unused")

      override suspend fun findById(id: UUID) = null

      override suspend fun findByApiId(apiId: String) = null

      override suspend fun findByPrimaryEmail(primaryEmail: String) = null
    }

  val bearerTokens: BearerTokenRepository =
    object : BearerTokenRepository {
      override suspend fun create(
        command: one.ztd.workbench.identity.model.CreateBearerTokenCommand
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
        emptyList<one.ztd.workbench.identity.permission.AdminUserRecord>()

      override suspend fun listInstanceAdmins() =
        emptyList<one.ztd.workbench.identity.permission.AdminUserRecord>()

      override suspend fun listTenantAdmins(tenantId: UUID) =
        emptyList<one.ztd.workbench.identity.permission.AdminUserRecord>()
    }

  val adminUserCommands: AdminUserCommandRepository =
    object : AdminUserCommandRepository {
      override suspend fun create(
        command: one.ztd.workbench.identity.permission.CreateAdminUserCommand
      ) = error("unused")

      override suspend fun revoke(id: UUID, revokedAt: OffsetDateTime) = false

      override suspend fun revokeByTenant(tenantId: UUID, revokedAt: OffsetDateTime) = 0
    }

  val accessGrants: AccessGrantRepository =
    object : AccessGrantRepository {
      override suspend fun create(
        command: one.ztd.workbench.identity.permission.CreateAccessGrantCommand
      ) = error("unused")

      override suspend fun findById(id: UUID) = null

      override suspend fun findByApiId(apiId: String) = null

      override suspend fun listBySubject(
        subjectUserId: UUID,
        scope: one.ztd.workbench.identity.permission.GrantScope?,
        tenantId: UUID?,
        projectId: UUID?,
      ) = emptyList<one.ztd.workbench.identity.permission.AccessGrantRecord>()

      override suspend fun listActiveForSubject(
        subjectUserId: UUID,
        scope: one.ztd.workbench.identity.permission.GrantScope,
        tenantId: UUID?,
        projectId: UUID?,
        at: OffsetDateTime,
      ) = emptyList<one.ztd.workbench.identity.permission.AccessGrantRecord>()

      override suspend fun listByTenant(tenantId: UUID) =
        emptyList<one.ztd.workbench.identity.permission.AccessGrantRecord>()

      override suspend fun listInstanceGrants() =
        emptyList<one.ztd.workbench.identity.permission.AccessGrantRecord>()

      override suspend fun expire(id: UUID, validTo: OffsetDateTime) = false

      override suspend fun expireByTenant(tenantId: UUID, expiredAt: OffsetDateTime) = 0
    }

  val projects: ProjectRepository =
    object : ProjectRepository {
      override suspend fun create(
        command: one.ztd.workbench.agile.project.model.CreateProjectCommand
      ) = error("unused")

      override suspend fun findByApiId(tenantId: UUID, apiId: String) = null

      override suspend fun findById(tenantId: UUID, id: UUID) = null

      override suspend fun list(tenantId: UUID, identifier: String?) =
        emptyList<one.ztd.workbench.agile.project.model.ProjectRecord>()

      override suspend fun update(
        command: one.ztd.workbench.agile.project.model.UpdateProjectCommand
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
        request: one.ztd.workbench.agile.project.ProjectDestroyRequest
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
        status: one.ztd.workbench.agile.project.model.ProjectStatus,
      ) = false
    }
}
