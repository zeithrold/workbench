package doa.ink.workbench.service.permission

import doa.ink.workbench.core.common.errors.ResourceNotFoundException
import doa.ink.workbench.core.identity.UserRepository
import doa.ink.workbench.core.identity.model.UserRecord
import doa.ink.workbench.core.permission.AccessGrantRecord
import doa.ink.workbench.core.permission.AccessGrantRepository
import doa.ink.workbench.core.permission.AdminScope
import doa.ink.workbench.core.permission.AdminUserRepository
import doa.ink.workbench.core.permission.CreateAccessGrantCommand
import doa.ink.workbench.core.permission.CreateAdminUserCommand
import doa.ink.workbench.core.permission.CreatePermissionActionCommand
import doa.ink.workbench.core.permission.GrantScope
import doa.ink.workbench.core.permission.PermissionActionRepository
import doa.ink.workbench.core.permission.model.AuthorizationAction
import doa.ink.workbench.core.permission.model.PermissionEffect
import doa.ink.workbench.core.project.ProjectRepository
import doa.ink.workbench.service.common.PublicIdResolver
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service

@Service
@Suppress("TooManyFunctions")
class AdminUserManagementService(
  private val adminUsers: AdminUserRepository,
  private val accessGrants: AccessGrantRepository,
  private val actions: PermissionActionRepository,
  private val userRepository: UserRepository,
  private val publicIds: PublicIdResolver,
  private val projects: ProjectRepository,
  private val clock: Clock,
) {
  suspend fun listInstanceAdmins(): List<AdminUserView> =
    adminUsers.listInstanceAdmins().map { AdminUserView.from(it, requireUser(it.userId)) }

  suspend fun listTenantAdmins(tenantId: UUID): List<AdminUserView> =
    adminUsers.listTenantAdmins(tenantId).map { AdminUserView.from(it, requireUser(it.userId)) }

  suspend fun grantInstanceAdmin(userPublicId: String, actorUserId: UUID?): AdminUserView {
    val user = publicIds.resolveUser(userPublicId)
    val now = now()
    val record =
      adminUsers.create(
        CreateAdminUserCommand(
          userId = user.id,
          scope = AdminScope.INSTANCE,
          grantedBy = actorUserId,
          validFrom = now,
        )
      )
    return AdminUserView.from(record, user)
  }

  suspend fun grantTenantAdmin(
    tenantId: UUID,
    userPublicId: String,
    actorUserId: UUID?,
  ): AdminUserView {
    val user = publicIds.resolveUser(userPublicId)
    val now = now()
    val record =
      adminUsers.create(
        CreateAdminUserCommand(
          userId = user.id,
          scope = AdminScope.TENANT,
          tenantId = tenantId,
          grantedBy = actorUserId,
          validFrom = now,
        )
      )
    return AdminUserView.from(record, user)
  }

  suspend fun revokeAdmin(publicId: String): Boolean {
    val admin = adminUsers.findByApiId(publicId) ?: return false
    return adminUsers.revoke(admin.id, now())
  }

  suspend fun listGrants(
    scope: GrantScope?,
    tenantId: UUID?,
    subjectUserId: UUID?,
  ): List<AccessGrantView> {
    val grants =
      when {
        scope == GrantScope.INSTANCE -> accessGrants.listInstanceGrants()
        tenantId != null -> accessGrants.listByTenant(tenantId)
        subjectUserId != null -> accessGrants.listBySubject(subjectUserId, scope, tenantId, null)
        else -> emptyList()
      }
    return grants.map { AccessGrantView.from(it) }
  }

  suspend fun createGrant(
    scope: GrantScope,
    tenantId: UUID?,
    userPublicId: String,
    actionCode: String,
    resourcePattern: String,
    effect: PermissionEffect,
    projectPublicId: String?,
    actorUserId: UUID?,
  ): AccessGrantView {
    val user = publicIds.resolveUser(userPublicId)
    val projectId = projectPublicId?.let { publicId ->
      require(tenantId != null) { "tenantId is required for project-scoped grants." }
      publicIds.resolveProject(tenantId, publicId).id
    }
    val record =
      accessGrants.create(
        CreateAccessGrantCommand(
          scope = scope,
          tenantId = tenantId,
          projectId = projectId,
          subjectUserId = user.id,
          action = AuthorizationAction(actionCode),
          resourcePattern = resourcePattern,
          effect = effect,
          validFrom = now(),
          grantedBy = actorUserId,
        )
      )
    return AccessGrantView.from(record)
  }

  suspend fun expireGrant(publicId: String): Boolean {
    val grant = accessGrants.findByApiId(publicId) ?: return false
    return accessGrants.expire(grant.id, now())
  }

  suspend fun listActions(): List<ActionView> = actions.list().map { ActionView.from(it) }

  suspend fun ensureAction(code: String, description: String?): ActionView =
    actions.upsert(CreatePermissionActionCommand(AuthorizationAction(code), description)).let {
      ActionView.from(it)
    }

  private suspend fun requireUser(userId: UUID): UserRecord =
    userRepository.findById(userId) ?: throw ResourceNotFoundException("User not found.")

  private fun now(): OffsetDateTime = OffsetDateTime.now(clock)
}

data class AdminUserView(
  val id: String,
  val userId: String,
  val scope: AdminScope,
  val tenantId: String?,
  val status: String,
  val validFrom: OffsetDateTime,
  val validTo: OffsetDateTime?,
) {
  companion object {
    fun from(record: doa.ink.workbench.core.permission.AdminUserRecord, user: UserRecord) =
      AdminUserView(
        id = record.apiId.value,
        userId = user.apiId.value,
        scope = record.scope,
        tenantId = record.tenantId?.toString(),
        status = record.status.dbValue,
        validFrom = record.validFrom,
        validTo = record.validTo,
      )
  }
}

data class AccessGrantView(
  val id: String,
  val scope: GrantScope,
  val tenantId: String?,
  val projectId: String?,
  val userId: String,
  val action: String,
  val resourcePattern: String,
  val effect: PermissionEffect,
  val validFrom: OffsetDateTime,
  val validTo: OffsetDateTime?,
) {
  companion object {
    fun from(record: AccessGrantRecord): AccessGrantView =
      AccessGrantView(
        id = record.apiId.value,
        scope = record.scope,
        tenantId = record.tenantId?.toString(),
        projectId = record.projectId?.toString(),
        userId = record.subjectUserId.toString(),
        action = record.action.code,
        resourcePattern = record.resourcePattern,
        effect = record.effect,
        validFrom = record.validFrom,
        validTo = record.validTo,
      )
  }
}

data class ActionView(
  val code: String,
  val description: String?,
) {
  companion object {
    fun from(record: doa.ink.workbench.core.permission.PermissionActionRecord): ActionView =
      ActionView(code = record.code.code, description = record.description)
  }
}
