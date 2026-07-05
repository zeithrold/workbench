package ink.doa.workbench.security.permission

import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.identity.model.CreateTenantMemberCommand
import ink.doa.workbench.core.identity.model.TenantMemberStatus
import ink.doa.workbench.core.identity.model.UserRecord
import ink.doa.workbench.core.permission.AdminScope
import ink.doa.workbench.core.permission.CreateAccessGrantCommand
import ink.doa.workbench.core.permission.CreateAdminUserCommand
import ink.doa.workbench.core.permission.GrantScope
import ink.doa.workbench.core.permission.model.AuthorizationAction
import ink.doa.workbench.security.common.PublicIdResolver
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service

private val DEFAULT_TENANT_ADMIN_GRANTS =
  listOf(
    AuthorizationAction("tenant.access") to "tenant:*",
    AuthorizationAction("project.create") to "project:*",
    AuthorizationAction("project.read") to "project:*",
    AuthorizationAction("project.manage") to "project:*",
    AuthorizationAction("permission.assignment.manage") to "permission:*",
    AuthorizationAction("permission.role.manage") to "permission:*",
    AuthorizationAction("permission.policy.manage") to "permission:*",
  )

@Service
class AdminUserService(
  private val persistence: AdminUserPersistenceSupport,
  private val publicIds: PublicIdResolver,
  private val clock: Clock,
  private val permissionBootstrapService: PermissionBootstrapService? = null,
) {
  suspend fun listInstanceAdmins(): List<AdminUserView> =
    persistence.adminUserQueries.listInstanceAdmins().map {
      AdminUserView.from(it, requireUser(it.userId))
    }

  suspend fun listTenantAdmins(tenantId: UUID): List<AdminUserView> =
    persistence.adminUserQueries.listTenantAdmins(tenantId).map {
      AdminUserView.from(it, requireUser(it.userId))
    }

  suspend fun grantInstanceAdmin(userPublicId: String, actorUserId: UUID?): AdminUserView {
    val user = publicIds.resolveUser(userPublicId)
    val now = now()
    val record =
      persistence.adminUserCommands.create(
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
      persistence.adminUserCommands.create(
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

  suspend fun provisionTenantAdmin(
    tenantId: UUID,
    userId: UUID,
    actorUserId: UUID?,
    membershipStatus: TenantMemberStatus = TenantMemberStatus.ACTIVE,
  ): AdminUserView {
    val user = requireUser(userId)
    val now = now()
    persistence.tenantMembers.create(
      CreateTenantMemberCommand(
        tenantId = tenantId,
        userId = userId,
        status = membershipStatus,
        joinedAt = if (membershipStatus == TenantMemberStatus.ACTIVE) now else null,
        invitedBy = actorUserId,
      )
    )
    val record =
      persistence.adminUserCommands.create(
        CreateAdminUserCommand(
          userId = userId,
          scope = AdminScope.TENANT,
          tenantId = tenantId,
          grantedBy = actorUserId,
          validFrom = now,
        )
      )
    DEFAULT_TENANT_ADMIN_GRANTS.forEach { (action, pattern) ->
      persistence.accessGrants.create(
        CreateAccessGrantCommand(
          scope = GrantScope.TENANT,
          tenantId = tenantId,
          subjectUserId = userId,
          action = action,
          resourcePattern = pattern,
          validFrom = now,
          grantedBy = actorUserId,
        )
      )
    }
    permissionBootstrapService?.provisionTenantAdmin(
      tenantId = tenantId,
      userId = userId,
      actorUserId = actorUserId,
    )
    return AdminUserView.from(record, user)
  }

  suspend fun revokeAdmin(publicId: String): Boolean {
    val admin = persistence.adminUserQueries.findByApiId(publicId) ?: return false
    return persistence.adminUserCommands.revoke(admin.id, now())
  }

  private suspend fun requireUser(userId: UUID): UserRecord =
    persistence.userRepository.findById(userId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND)

  private fun now(): OffsetDateTime = OffsetDateTime.now(clock)
}
