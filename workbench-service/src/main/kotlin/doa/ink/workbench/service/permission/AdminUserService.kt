package doa.ink.workbench.service.permission

import doa.ink.workbench.core.common.errors.ResourceNotFoundException
import doa.ink.workbench.core.identity.TenantMemberRepository
import doa.ink.workbench.core.identity.UserRepository
import doa.ink.workbench.core.identity.model.CreateTenantMemberCommand
import doa.ink.workbench.core.identity.model.TenantMemberStatus
import doa.ink.workbench.core.identity.model.UserRecord
import doa.ink.workbench.core.permission.AccessGrantRepository
import doa.ink.workbench.core.permission.AdminScope
import doa.ink.workbench.core.permission.AdminUserCommandRepository
import doa.ink.workbench.core.permission.AdminUserQueryRepository
import doa.ink.workbench.core.permission.CreateAccessGrantCommand
import doa.ink.workbench.core.permission.CreateAdminUserCommand
import doa.ink.workbench.core.permission.GrantScope
import doa.ink.workbench.core.permission.model.AuthorizationAction
import doa.ink.workbench.service.common.PublicIdResolver
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
  private val adminUserCommands: AdminUserCommandRepository,
  private val adminUserQueries: AdminUserQueryRepository,
  private val accessGrants: AccessGrantRepository,
  private val userRepository: UserRepository,
  private val tenantMembers: TenantMemberRepository,
  private val publicIds: PublicIdResolver,
  private val clock: Clock,
) {
  suspend fun listInstanceAdmins(): List<AdminUserView> =
    adminUserQueries.listInstanceAdmins().map { AdminUserView.from(it, requireUser(it.userId)) }

  suspend fun listTenantAdmins(tenantId: UUID): List<AdminUserView> =
    adminUserQueries.listTenantAdmins(tenantId).map {
      AdminUserView.from(it, requireUser(it.userId))
    }

  suspend fun grantInstanceAdmin(userPublicId: String, actorUserId: UUID?): AdminUserView {
    val user = publicIds.resolveUser(userPublicId)
    val now = now()
    val record =
      adminUserCommands.create(
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
      adminUserCommands.create(
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
    tenantMembers.create(
      CreateTenantMemberCommand(
        tenantId = tenantId,
        userId = userId,
        status = membershipStatus,
        joinedAt = if (membershipStatus == TenantMemberStatus.ACTIVE) now else null,
        invitedBy = actorUserId,
      )
    )
    val record =
      adminUserCommands.create(
        CreateAdminUserCommand(
          userId = userId,
          scope = AdminScope.TENANT,
          tenantId = tenantId,
          grantedBy = actorUserId,
          validFrom = now,
        )
      )
    DEFAULT_TENANT_ADMIN_GRANTS.forEach { (action, pattern) ->
      accessGrants.create(
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
    return AdminUserView.from(record, user)
  }

  suspend fun revokeAdmin(publicId: String): Boolean {
    val admin = adminUserQueries.findByApiId(publicId) ?: return false
    return adminUserCommands.revoke(admin.id, now())
  }

  private suspend fun requireUser(userId: UUID): UserRecord =
    userRepository.findById(userId) ?: throw ResourceNotFoundException("User not found.")

  private fun now(): OffsetDateTime = OffsetDateTime.now(clock)
}
