package ink.doa.workbench.application.identity

import ink.doa.workbench.application.permission.AdminUserService
import ink.doa.workbench.identity.TenantMemberRepository
import ink.doa.workbench.identity.UserRepository
import ink.doa.workbench.identity.common.summary.UserSummary
import ink.doa.workbench.identity.model.TenantMemberStatus
import ink.doa.workbench.identity.permission.AdminUserQueryRepository
import ink.doa.workbench.kernel.common.errors.ResourceConflictException
import ink.doa.workbench.kernel.common.errors.ResourceNotFoundException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service

data class TenantMemberView(
  val id: String,
  val user: UserSummary,
  val status: TenantMemberStatus,
  val administrator: Boolean,
  val joinedAt: OffsetDateTime?,
)

@Service
class TenantMemberManagementService(
  private val members: TenantMemberRepository,
  private val users: UserRepository,
  private val adminQueries: AdminUserQueryRepository,
  private val admins: AdminUserService,
  private val clock: Clock,
) {
  suspend fun list(tenantId: UUID): List<TenantMemberView> =
    members.listByTenant(tenantId).map { member ->
      val user =
        users.findById(member.userId)
          ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND)
      TenantMemberView(
        id = member.apiId.value,
        user = UserSummary.from(user),
        status = member.status,
        administrator = adminQueries.isActiveTenantAdmin(tenantId, member.userId, now()),
        joinedAt = member.joinedAt,
      )
    }

  suspend fun suspendMember(tenantId: UUID, memberId: String): TenantMemberView =
    transition(tenantId, memberId, TenantMemberStatus.SUSPENDED)

  suspend fun restoreMember(tenantId: UUID, memberId: String): TenantMemberView =
    transition(tenantId, memberId, TenantMemberStatus.ACTIVE)

  suspend fun removeMember(tenantId: UUID, memberId: String): TenantMemberView =
    transition(tenantId, memberId, TenantMemberStatus.REMOVED)

  private suspend fun transition(
    tenantId: UUID,
    memberId: String,
    targetStatus: TenantMemberStatus,
  ): TenantMemberView {
    val member = requireMember(tenantId, memberId)
    val activeAdmin = adminQueries.findActiveTenantAdmin(tenantId, member.userId, now())
    if (targetStatus != TenantMemberStatus.ACTIVE && activeAdmin != null) {
      if (adminQueries.listTenantAdmins(tenantId).size <= 1) {
        throw ResourceConflictException(WorkbenchErrorCode.TENANT_LAST_ADMIN_REQUIRED)
      }
      admins.revokeTenantAdmin(tenantId, activeAdmin.apiId.value)
    }
    val updated = requireNotNull(members.updateStatus(member.id, targetStatus, now()))
    val user = requireUser(updated.userId)
    return TenantMemberView(
      id = updated.apiId.value,
      user = UserSummary.from(user),
      status = updated.status,
      administrator = adminQueries.isActiveTenantAdmin(tenantId, updated.userId, now()),
      joinedAt = updated.joinedAt,
    )
  }

  private suspend fun requireMember(tenantId: UUID, memberId: String) =
    members.findByApiId(tenantId, memberId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_TENANT_MEMBER_NOT_FOUND)

  private suspend fun requireUser(userId: UUID) =
    users.findById(userId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND)

  private fun requireNotNull(member: ink.doa.workbench.identity.model.TenantMemberRecord?) =
    member ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_TENANT_MEMBER_NOT_FOUND)

  private fun now(): OffsetDateTime = OffsetDateTime.now(clock)
}
