package ink.doa.workbench.agile.project

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.errors.requireValid
import ink.doa.workbench.core.common.summary.UserSummary
import ink.doa.workbench.core.identity.TenantMemberRepository
import ink.doa.workbench.core.identity.UserRepository
import ink.doa.workbench.core.identity.model.TenantMemberStatus
import ink.doa.workbench.core.permission.CreatePermissionBindingCommand
import ink.doa.workbench.core.permission.PermissionBindingRepository
import ink.doa.workbench.core.permission.PermissionPolicyRecord
import ink.doa.workbench.core.permission.PermissionPolicyRepository
import ink.doa.workbench.core.permission.PermissionPrincipalType
import ink.doa.workbench.core.project.ProjectRepository
import ink.doa.workbench.core.project.model.NonMemberJoinPolicy
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service

private val BUILTIN_ROLE_CODES =
  mapOf(
    "admin" to "project-admin",
    "member" to "project-member",
    "viewer" to "project-viewer",
  )

@Service
class ProjectMemberService(
  private val bindings: PermissionBindingRepository,
  private val policies: PermissionPolicyRepository,
  private val projects: ProjectRepository,
  private val users: UserRepository,
  private val tenantMembers: TenantMemberRepository,
  private val clock: Clock,
) {
  suspend fun listMembers(tenantId: UUID, projectId: UUID): List<ProjectMemberView> {
    val bindingViews =
      bindings.listByProject(tenantId, projectId).filter {
        it.principalType == PermissionPrincipalType.USER && it.principalUserId != null
      }
    return bindingViews
      .mapNotNull { binding ->
        binding.principalUserId?.let { userId -> userId to binding }
      }
      .groupBy({ it.first }, { it.second })
      .map { (userId, memberBindings) ->
        val user =
          users.findById(userId)?.let(UserSummary::from)
            ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND)
        ProjectMemberView(
          user = user,
          policies =
            memberBindings
              .filter { it.validTo == null }
              .map { binding ->
                val policy =
                  policies.findById(tenantId, binding.policyId)
                    ?: throw ResourceNotFoundException(
                      WorkbenchErrorCode.RESOURCE_PERMISSION_POLICY_NOT_FOUND
                    )
                ProjectMemberPolicyView(
                  bindingId = binding.apiId.value,
                  policy = ProjectPermissionPolicySummary.from(policy),
                )
              },
        )
      }
  }

  suspend fun addMember(command: ProjectMemberMutationCommand): ProjectMemberView {
    val user = requireUser(command.userPublicId)
    requireActiveTenantMember(command.tenantId, user.id)
    val policy = resolvePolicy(command.tenantId, command.policyPublicId, command.role)
    bindings.create(
      CreatePermissionBindingCommand(
        tenantId = command.tenantId,
        projectId = command.projectId,
        principalType = PermissionPrincipalType.USER,
        principalUserId = user.id,
        principalGroupId = null,
        policyId = policy.id,
        validFrom = OffsetDateTime.now(clock),
        createdBy = command.actorUserId,
      )
    )
    return listMembers(command.tenantId, command.projectId).single {
      it.user.id == user.apiId.value
    }
  }

  suspend fun attachPolicy(command: ProjectMemberMutationCommand): ProjectMemberView {
    requireActiveTenantMember(command.tenantId, requireUser(command.userPublicId).id)
    return addMember(command)
  }

  suspend fun removePolicy(
    tenantId: UUID,
    bindingPublicId: String,
  ): Boolean {
    val binding =
      bindings.findByApiId(tenantId, bindingPublicId)
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PERMISSION_BINDING_NOT_FOUND)
    return bindings.expire(tenantId, binding.id, OffsetDateTime.now(clock))
  }

  suspend fun join(
    tenantId: UUID,
    projectId: UUID,
    userId: UUID,
    actorUserId: UUID?,
  ): ProjectMemberView {
    requireOpenJoinProject(tenantId, projectId)
    val user = requireActiveTenantMember(tenantId, userId)
    val memberPolicy = requireProjectMemberPolicy(tenantId)
    bindings.create(
      CreatePermissionBindingCommand(
        tenantId = tenantId,
        projectId = projectId,
        principalType = PermissionPrincipalType.USER,
        principalUserId = user.id,
        principalGroupId = null,
        policyId = memberPolicy.id,
        validFrom = OffsetDateTime.now(clock),
        createdBy = actorUserId,
      )
    )
    return listMembers(tenantId, projectId).single { it.user.id == user.apiId.value }
  }

  private suspend fun requireOpenJoinProject(tenantId: UUID, projectId: UUID) {
    val project =
      projects.findById(tenantId, projectId)
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PROJECT_NOT_FOUND)
    requireValid(
      project.nonMemberJoinPolicy == NonMemberJoinPolicy.OPEN,
      WorkbenchErrorCode.PROJECT_SELF_JOIN_DISABLED,
    )
  }

  private suspend fun requireProjectMemberPolicy(tenantId: UUID) =
    policies.findByCode(tenantId, "project-member")
      ?: throw ResourceNotFoundException(
        WorkbenchErrorCode.RESOURCE_PROJECT_MEMBER_POLICY_NOT_FOUND
      )

  private suspend fun requireActiveTenantMember(tenantId: UUID, userId: UUID) =
    tenantMembers
      .findByTenantAndUser(tenantId, userId)
      ?.takeIf {
        it.status == TenantMemberStatus.ACTIVE
      }
      ?.let { users.findById(userId) }
      ?: throw InvalidRequestException(WorkbenchErrorCode.PROJECT_MEMBER_INACTIVE_TENANT_MEMBER)

  private suspend fun requireUser(userPublicId: String) =
    users.findByApiId(userPublicId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND)

  private suspend fun resolvePolicy(
    tenantId: UUID,
    policyPublicId: String?,
    role: String?,
  ) =
    when {
      policyPublicId != null ->
        policies.findByApiId(tenantId, policyPublicId)
          ?: throw ResourceNotFoundException(
            WorkbenchErrorCode.RESOURCE_PERMISSION_POLICY_NOT_FOUND
          )
      role != null -> {
        val code =
          BUILTIN_ROLE_CODES[role.lowercase()]
            ?: throw InvalidRequestException(
              WorkbenchErrorCode.PROJECT_MEMBER_UNKNOWN_ROLE,
              "Unknown role: $role",
            )
        policies.findByCode(tenantId, code)
          ?: throw ResourceNotFoundException(
            WorkbenchErrorCode.RESOURCE_PERMISSION_POLICY_NOT_FOUND
          )
      }
      else ->
        throw InvalidRequestException(WorkbenchErrorCode.PROJECT_MEMBER_POLICY_OR_ROLE_REQUIRED)
    }
}

data class ProjectMemberView(
  val user: UserSummary,
  val policies: List<ProjectMemberPolicyView>,
)

data class ProjectMemberPolicyView(
  val bindingId: String,
  val policy: ProjectPermissionPolicySummary,
)

data class ProjectPermissionPolicySummary(
  val id: String,
  val code: String,
  val name: String,
) {
  companion object {
    fun from(record: PermissionPolicyRecord) =
      ProjectPermissionPolicySummary(
        id = record.apiId.value,
        code = record.code,
        name = record.name,
      )
  }
}
