package doa.ink.workbench.service.permission

import doa.ink.workbench.core.common.errors.InvalidRequestException
import doa.ink.workbench.core.identity.UserRepository
import doa.ink.workbench.core.permission.AssignRoleCommand
import doa.ink.workbench.core.permission.CreatePermissionActionCommand
import doa.ink.workbench.core.permission.CreatePermissionPolicyCommand
import doa.ink.workbench.core.permission.CreateRoleCommand
import doa.ink.workbench.core.permission.PermissionActionRepository
import doa.ink.workbench.core.permission.PermissionPolicyRepository
import doa.ink.workbench.core.permission.RoleAssignmentRepository
import doa.ink.workbench.core.permission.RoleRepository
import doa.ink.workbench.core.permission.RoleScope
import doa.ink.workbench.core.permission.model.AuthorizationAction
import doa.ink.workbench.core.permission.model.PermissionCondition
import doa.ink.workbench.core.permission.model.PermissionEffect
import doa.ink.workbench.core.project.ProjectRepository
import doa.ink.workbench.service.common.PublicIdResolver
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
  private val users: UserRepository,
  private val projects: ProjectRepository,
  private val publicIds: PublicIdResolver,
  private val clock: Clock,
) {
  suspend fun listRoles(tenantId: UUID): List<RoleView> =
    roles.list(tenantId).map { RoleView.from(it) }

  suspend fun createRole(
    tenantId: UUID,
    scope: RoleScope,
    code: String,
    name: String,
    description: String?,
  ): RoleView =
    roles
      .create(
        CreateRoleCommand(
          tenantId = tenantId,
          scope = scope,
          code = code,
          name = name,
          description = description,
        )
      )
      .let { RoleView.from(it) }

  suspend fun listActions(): List<ActionView> = actions.list().map { ActionView.from(it) }

  suspend fun ensureAction(code: String, description: String?): ActionView =
    actions.upsert(CreatePermissionActionCommand(AuthorizationAction(code), description)).let {
      ActionView.from(it)
    }

  suspend fun listPolicies(tenantId: UUID): List<PolicyView> {
    val roleById = roles.list(tenantId).associateBy { it.id }
    return policies.listByTenant(tenantId).map { policy ->
      val rolePublicId =
        roleById[policy.roleId]?.apiId?.value
          ?: throw InvalidRequestException("Role not found for policy ${policy.apiId.value}.")
      PolicyView.from(policy, rolePublicId)
    }
  }

  suspend fun createPolicy(
    tenantId: UUID,
    rolePublicId: String,
    actionCode: String,
    effect: PermissionEffect,
    resourcePattern: String,
    condition: PermissionCondition?,
    actorUserId: UUID?,
  ): PolicyView {
    val role = publicIds.resolveRole(tenantId, rolePublicId)
    val action = AuthorizationAction(actionCode)
    actions.findByCode(action)
      ?: throw InvalidRequestException("Unknown permission action: $actionCode")
    val policy =
      policies.create(
        CreatePermissionPolicyCommand(
          tenantId = tenantId,
          roleId = role.id,
          action = action,
          effect = effect,
          resourcePattern = resourcePattern,
          condition = condition,
          validFrom = OffsetDateTime.now(clock),
          createdBy = actorUserId,
        )
      )
    return PolicyView.from(policy, role.apiId.value)
  }

  suspend fun expirePolicy(tenantId: UUID, policyPublicId: String) {
    val policy = publicIds.resolvePolicy(tenantId, policyPublicId)
    policies.expire(policy.id, OffsetDateTime.now(clock))
  }

  suspend fun listAssignments(tenantId: UUID): List<RoleAssignmentView> {
    val roleById = roles.list(tenantId).associateBy { it.id }
    return assignments.listByTenant(tenantId).map { assignment ->
      val user =
        users.findById(assignment.userId)
          ?: throw InvalidRequestException(
            "User not found for assignment ${assignment.apiId.value}."
          )
      val role =
        roleById[assignment.roleId]
          ?: throw InvalidRequestException(
            "Role not found for assignment ${assignment.apiId.value}."
          )
      val projectPublicId =
        assignment.projectId?.let { projectId ->
          projects.findById(tenantId, projectId)?.apiId?.value
        }
      RoleAssignmentView.from(
        record = assignment,
        userPublicId = user.apiId.value,
        rolePublicId = role.apiId.value,
        projectPublicId = projectPublicId,
      )
    }
  }

  suspend fun assignRole(
    tenantId: UUID,
    userPublicId: String,
    rolePublicId: String,
    projectPublicId: String?,
    actorUserId: UUID?,
  ): RoleAssignmentView {
    val user = publicIds.resolveUser(userPublicId)
    val role = publicIds.resolveRole(tenantId, rolePublicId)
    val projectId = projectPublicId?.let { publicIds.resolveProject(tenantId, it).id }
    val assignment =
      assignments.assign(
        AssignRoleCommand(
          tenantId = tenantId,
          userId = user.id,
          roleId = role.id,
          projectId = projectId,
          grantedBy = actorUserId,
          validFrom = OffsetDateTime.now(clock),
        )
      )
    return RoleAssignmentView.from(
      record = assignment,
      userPublicId = user.apiId.value,
      rolePublicId = role.apiId.value,
      projectPublicId = projectPublicId,
    )
  }

  suspend fun revokeAssignment(tenantId: UUID, assignmentPublicId: String) {
    val assignment = publicIds.resolveAssignment(tenantId, assignmentPublicId)
    assignments.revoke(assignment.id, OffsetDateTime.now(clock))
  }
}
