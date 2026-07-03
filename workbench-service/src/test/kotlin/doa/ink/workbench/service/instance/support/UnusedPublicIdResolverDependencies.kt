package doa.ink.workbench.service.instance.support

import doa.ink.workbench.core.identity.LoginAccountRepository
import doa.ink.workbench.core.identity.UserRepository
import doa.ink.workbench.core.identity.auth.BearerTokenRepository
import doa.ink.workbench.core.permission.AccessGrantRepository
import doa.ink.workbench.core.permission.AdminUserRepository
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

  val adminUsers: AdminUserRepository =
    object : AdminUserRepository {
      override suspend fun create(
        command: doa.ink.workbench.core.permission.CreateAdminUserCommand
      ) = error("unused")

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
        emptyList<doa.ink.workbench.core.permission.AdminUserRecord>()

      override suspend fun listInstanceAdmins() =
        emptyList<doa.ink.workbench.core.permission.AdminUserRecord>()

      override suspend fun listTenantAdmins(tenantId: UUID) =
        emptyList<doa.ink.workbench.core.permission.AdminUserRecord>()

      override suspend fun revoke(id: UUID, revokedAt: OffsetDateTime) = false
    }

  val accessGrants: AccessGrantRepository =
    object : AccessGrantRepository {
      override suspend fun create(
        command: doa.ink.workbench.core.permission.CreateAccessGrantCommand
      ) = error("unused")

      override suspend fun findById(id: UUID) = null

      override suspend fun findByApiId(apiId: String) = null

      override suspend fun listBySubject(
        subjectUserId: UUID,
        scope: doa.ink.workbench.core.permission.GrantScope?,
        tenantId: UUID?,
        projectId: UUID?,
      ) = emptyList<doa.ink.workbench.core.permission.AccessGrantRecord>()

      override suspend fun listActiveForSubject(
        subjectUserId: UUID,
        scope: doa.ink.workbench.core.permission.GrantScope,
        tenantId: UUID?,
        projectId: UUID?,
        at: OffsetDateTime,
      ) = emptyList<doa.ink.workbench.core.permission.AccessGrantRecord>()

      override suspend fun listByTenant(tenantId: UUID) =
        emptyList<doa.ink.workbench.core.permission.AccessGrantRecord>()

      override suspend fun listInstanceGrants() =
        emptyList<doa.ink.workbench.core.permission.AccessGrantRecord>()

      override suspend fun expire(id: UUID, validTo: OffsetDateTime) = false
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
