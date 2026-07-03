package doa.ink.workbench.service.project

import doa.ink.workbench.core.common.errors.InvalidRequestException
import doa.ink.workbench.core.common.errors.ResourceNotFoundException
import doa.ink.workbench.core.common.summary.UserSummary
import doa.ink.workbench.core.identity.TenantMemberRepository
import doa.ink.workbench.core.identity.UserRepository
import doa.ink.workbench.core.identity.model.TenantMemberStatus
import doa.ink.workbench.core.permission.CreatePermissionBindingCommand
import doa.ink.workbench.core.permission.PermissionBindingRepository
import doa.ink.workbench.core.permission.PermissionPolicyRepository
import doa.ink.workbench.core.permission.PermissionPrincipalType
import doa.ink.workbench.core.project.ProjectRepository
import doa.ink.workbench.core.project.model.NonMemberJoinPolicy
import doa.ink.workbench.service.common.PublicIdResolver
import doa.ink.workbench.service.permission.PermissionManagementService
import doa.ink.workbench.service.permission.PermissionPolicySummary
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
  private val publicIds: PublicIdResolver,
  private val permissionManagement: PermissionManagementService,
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
                  policy = PermissionPolicySummary.from(policy),
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
    val user = requireActiveTenantMember(tenantId, publicIds.resolveUser(userPublicId).id)
    val policy = resolvePolicy(tenantId, policyPublicId, role)
    permissionManagement.createBinding(
      tenantId = tenantId,
      principalType = PermissionPrincipalType.USER,
      userPublicId = user.apiId.value,
      groupPublicId = null,
      policyPublicId = policy.apiId.value,
      projectPublicId = projects.findById(tenantId, projectId)?.apiId?.value,
      effect = null,
      actorUserId = actorUserId,
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
    requireActiveTenantMember(tenantId, publicIds.resolveUser(userPublicId).id)
    return addMember(tenantId, projectId, userPublicId, policyPublicId, role, actorUserId)
  }

  suspend fun removePolicy(
    tenantId: UUID,
    bindingPublicId: String,
  ): Boolean = permissionManagement.expireBinding(tenantId, bindingPublicId)

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
  val policy: PermissionPolicySummary,
)
