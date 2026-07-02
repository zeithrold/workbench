package doa.ink.workbench.service.permission

import doa.ink.workbench.core.common.errors.InvalidRequestException
import doa.ink.workbench.core.permission.AssignRoleCommand
import doa.ink.workbench.core.permission.CreatePermissionActionCommand
import doa.ink.workbench.core.permission.CreatePermissionPolicyCommand
import doa.ink.workbench.core.permission.CreateRoleCommand
import doa.ink.workbench.core.permission.PermissionActionRecord
import doa.ink.workbench.core.permission.PermissionActionRepository
import doa.ink.workbench.core.permission.PermissionPolicyRecord
import doa.ink.workbench.core.permission.PermissionPolicyRepository
import doa.ink.workbench.core.permission.RoleAssignmentRecord
import doa.ink.workbench.core.permission.RoleAssignmentRepository
import doa.ink.workbench.core.permission.RoleRecord
import doa.ink.workbench.core.permission.RoleRepository
import doa.ink.workbench.core.permission.RoleScope
import doa.ink.workbench.core.permission.model.AuthorizationAction
import doa.ink.workbench.core.permission.model.PermissionCondition
import doa.ink.workbench.core.permission.model.PermissionEffect
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class PermissionManagementService(
  private val roles: RoleRepository,
  private val actions: PermissionActionRepository,
  private val policies: PermissionPolicyRepository,
  private val assignments: RoleAssignmentRepository,
  private val clock: Clock,
) {
  suspend fun listRoles(tenantId: UUID): List<RoleRecord> = roles.list(tenantId)

  suspend fun createRole(
    tenantId: UUID,
    scope: RoleScope,
    code: String,
    name: String,
    description: String?,
  ): RoleRecord =
    roles.create(
      CreateRoleCommand(
        tenantId = tenantId,
        scope = scope,
        code = code,
        name = name,
        description = description,
      )
    )

  suspend fun listActions(): List<PermissionActionRecord> = actions.list()

  suspend fun ensureAction(code: String, description: String?): PermissionActionRecord =
    actions.upsert(CreatePermissionActionCommand(AuthorizationAction(code), description))

  suspend fun listPolicies(tenantId: UUID): List<PermissionPolicyRecord> =
    policies.listByTenant(tenantId)

  suspend fun createPolicy(
    tenantId: UUID,
    roleId: UUID,
    actionCode: String,
    effect: PermissionEffect,
    resourcePattern: String,
    condition: PermissionCondition?,
    actorUserId: UUID?,
  ): PermissionPolicyRecord {
    val action = AuthorizationAction(actionCode)
    actions.findByCode(action)
      ?: throw InvalidRequestException("Unknown permission action: $actionCode")
    return policies.create(
      CreatePermissionPolicyCommand(
        tenantId = tenantId,
        roleId = roleId,
        action = action,
        effect = effect,
        resourcePattern = resourcePattern,
        condition = condition,
        validFrom = OffsetDateTime.now(clock),
        createdBy = actorUserId,
      )
    )
  }

  suspend fun expirePolicy(id: UUID): Boolean = policies.expire(id, OffsetDateTime.now(clock))

  suspend fun listAssignments(tenantId: UUID): List<RoleAssignmentRecord> =
    assignments.listByTenant(tenantId)

  suspend fun assignRole(
    tenantId: UUID,
    userId: UUID,
    roleId: UUID,
    projectId: UUID?,
    actorUserId: UUID?,
  ): RoleAssignmentRecord =
    assignments.assign(
      AssignRoleCommand(
        tenantId = tenantId,
        userId = userId,
        roleId = roleId,
        projectId = projectId,
        grantedBy = actorUserId,
        validFrom = OffsetDateTime.now(clock),
      )
    )

  suspend fun revokeAssignment(id: UUID): Boolean =
    assignments.revoke(id, OffsetDateTime.now(clock))
}
