package doa.ink.workbench.agile.project

import doa.ink.workbench.core.common.errors.InvalidRequestException
import doa.ink.workbench.core.common.errors.ResourceNotFoundException
import doa.ink.workbench.core.common.summary.UserSummary
import doa.ink.workbench.core.identity.TenantMemberRepository
import doa.ink.workbench.core.identity.UserRepository
import doa.ink.workbench.core.identity.model.TenantMemberStatus
import doa.ink.workbench.core.permission.CreatePermissionBindingCommand
import doa.ink.workbench.core.permission.PermissionBindingRepository
import doa.ink.workbench.core.permission.PermissionPolicyRecord
import doa.ink.workbench.core.permission.PermissionPolicyRepository
import doa.ink.workbench.core.permission.PermissionPrincipalType
import doa.ink.workbench.core.project.ProjectRepository
import doa.ink.workbench.core.project.model.NonMemberJoinPolicy
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
      .groupBy { it.principalUserId!! }
      .map { (userId, memberBindings) ->
        val user =
          users.findById(userId)?.let(UserSummary::from)
            ?: throw ResourceNotFoundException("User not found.")
        ProjectMemberView(
          user = user,
          policies =
            memberBindings
              .filter { it.validTo == null }
              .map { binding ->
                val policy =
                  policies.findById(tenantId, binding.policyId)
                    ?: throw ResourceNotFoundException("Permission policy not found.")
                ProjectMemberPolicyView(
                  bindingId = binding.apiId.value,
                  policy = ProjectPermissionPolicySummary.from(policy),
                )
              },
        )
      }
  }

  suspend fun addMember(
    tenantId: UUID,
    projectId: UUID,
    userPublicId: String,
    policyPublicId: String?,
    role: String?,
    actorUserId: UUID?,
  ): ProjectMemberView {
    val user = requireUser(userPublicId)
    requireActiveTenantMember(tenantId, user.id)
    val policy = resolvePolicy(tenantId, policyPublicId, role)
    bindings.create(
      CreatePermissionBindingCommand(
        tenantId = tenantId,
        projectId = projectId,
        principalType = PermissionPrincipalType.USER,
        principalUserId = user.id,
        principalGroupId = null,
        policyId = policy.id,
        validFrom = OffsetDateTime.now(clock),
        createdBy = actorUserId,
      )
    )
    return listMembers(tenantId, projectId).single { it.user.id == user.apiId.value }
  }

  suspend fun attachPolicy(
    tenantId: UUID,
    projectId: UUID,
    userPublicId: String,
    policyPublicId: String?,
    role: String?,
    actorUserId: UUID?,
  ): ProjectMemberView {
    requireActiveTenantMember(tenantId, requireUser(userPublicId).id)
    return addMember(tenantId, projectId, userPublicId, policyPublicId, role, actorUserId)
  }

  suspend fun removePolicy(
    tenantId: UUID,
    bindingPublicId: String,
  ): Boolean {
    val binding =
      bindings.findByApiId(tenantId, bindingPublicId)
        ?: throw ResourceNotFoundException("Permission binding not found.")
    return bindings.expire(tenantId, binding.id, OffsetDateTime.now(clock))
  }

  @Suppress("ThrowsCount")
  suspend fun join(
    tenantId: UUID,
    projectId: UUID,
    userId: UUID,
    actorUserId: UUID?,
  ): ProjectMemberView {
    val project =
      projects.findById(tenantId, projectId)
        ?: throw ResourceNotFoundException("Project not found.")
    if (project.nonMemberJoinPolicy != NonMemberJoinPolicy.OPEN) {
      throw InvalidRequestException("This project does not allow self-join.")
    }
    val user = requireActiveTenantMember(tenantId, userId)
    val memberPolicy =
      policies.findByCode(tenantId, "project-member")
        ?: throw ResourceNotFoundException("Built-in project-member policy is not configured.")
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

  private suspend fun requireActiveTenantMember(tenantId: UUID, userId: UUID) =
    tenantMembers
      .findByTenantAndUser(tenantId, userId)
      ?.takeIf {
        it.status == TenantMemberStatus.ACTIVE
      }
      ?.let { users.findById(userId) }
      ?: throw InvalidRequestException("User is not an active tenant member.")

  private suspend fun requireUser(userPublicId: String) =
    users.findByApiId(userPublicId) ?: throw ResourceNotFoundException("User not found.")

  private suspend fun resolvePolicy(
    tenantId: UUID,
    policyPublicId: String?,
    role: String?,
  ) =
    when {
      policyPublicId != null ->
        policies.findByApiId(tenantId, policyPublicId)
          ?: throw ResourceNotFoundException("Permission policy not found.")
      role != null -> {
        val code =
          BUILTIN_ROLE_CODES[role.lowercase()]
            ?: throw InvalidRequestException("Unknown role: $role")
        policies.findByCode(tenantId, code)
          ?: throw ResourceNotFoundException("Permission policy not found.")
      }
      else -> throw InvalidRequestException("Either policyId or role is required.")
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
