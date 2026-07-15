package ink.doa.workbench.application.permission

import ink.doa.workbench.application.identity.PublicIdResolver
import ink.doa.workbench.identity.model.CreateTenantMemberCommand
import ink.doa.workbench.identity.model.TenantMemberStatus
import ink.doa.workbench.identity.model.UserRecord
import ink.doa.workbench.identity.permission.AdminScope
import ink.doa.workbench.identity.permission.CreateAccessGrantCommand
import ink.doa.workbench.identity.permission.CreateAdminUserCommand
import ink.doa.workbench.identity.permission.GrantScope
import ink.doa.workbench.identity.permission.model.AuthorizationAction
import ink.doa.workbench.kernel.common.errors.ResourceConflictException
import ink.doa.workbench.kernel.common.errors.ResourceNotFoundException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
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
  private val instanceAdminGrantProvisioner: InstanceAdminGrantProvisioner =
    InstanceAdminGrantProvisioner(persistence.accessGrants),
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
    instanceAdminGrantProvisioner.provision(user.id, actorUserId, now)
    return AdminUserView.from(record, user)
  }

  suspend fun grantTenantAdmin(
    tenantId: UUID,
    userPublicId: String,
    actorUserId: UUID?,
  ): AdminUserView {
    val user = publicIds.resolveUser(userPublicId)
    val now = now()
    val membership = persistence.tenantMembers.findByTenantAndUser(tenantId, user.id)
    if (membership == null) {
      persistence.tenantMembers.create(
        CreateTenantMemberCommand(
          tenantId = tenantId,
          userId = user.id,
          status = TenantMemberStatus.ACTIVE,
          joinedAt = now,
          invitedBy = actorUserId,
        )
      )
    } else if (membership.status != TenantMemberStatus.ACTIVE) {
      persistence.tenantMembers.updateStatus(membership.id, TenantMemberStatus.ACTIVE, now)
    }
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
    permissionBootstrapService?.provisionTenantAdmin(tenantId, user.id, actorUserId)
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

  suspend fun revokeInstanceAdmin(publicId: String): Boolean {
    val admin = persistence.adminUserQueries.findByApiId(publicId) ?: return false
    if (admin.scope != AdminScope.INSTANCE) return false
    if (persistence.adminUserQueries.listInstanceAdmins().size <= 1) {
      throw ResourceConflictException(WorkbenchErrorCode.INSTANCE_LAST_ADMIN_REQUIRED)
    }
    val revokedAt = now()
    persistence.accessGrants
      .listBySubject(admin.userId, GrantScope.INSTANCE, null, null)
      .filter { it.validTo == null }
      .forEach { persistence.accessGrants.expire(it.id, revokedAt) }
    return persistence.adminUserCommands.revoke(admin.id, revokedAt)
  }

  suspend fun revokeTenantAdmin(tenantId: UUID, publicId: String): Boolean {
    val admin = persistence.adminUserQueries.findByApiId(publicId) ?: return false
    if (admin.scope != AdminScope.TENANT || admin.tenantId != tenantId) return false
    if (persistence.adminUserQueries.listTenantAdmins(tenantId).size <= 1) {
      throw ResourceConflictException(WorkbenchErrorCode.TENANT_LAST_ADMIN_REQUIRED)
    }
    val revokedAt = now()
    permissionBootstrapService?.revokeTenantAdmin(tenantId, admin.userId)
    persistence.accessGrants
      .listBySubject(admin.userId, GrantScope.TENANT, tenantId, null)
      .filter { it.validTo == null }
      .forEach { persistence.accessGrants.expire(it.id, revokedAt) }
    return persistence.adminUserCommands.revoke(admin.id, revokedAt)
  }

  private suspend fun requireUser(userId: UUID): UserRecord =
    persistence.userRepository.findById(userId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND)

  private fun now(): OffsetDateTime = OffsetDateTime.now(clock)
}
