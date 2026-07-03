package doa.ink.workbench.service.permission

import doa.ink.workbench.core.common.errors.InvalidRequestException
import doa.ink.workbench.core.common.errors.ResourceNotFoundException
import doa.ink.workbench.core.common.summary.ProjectSummary
import doa.ink.workbench.core.common.summary.UserSummary
import doa.ink.workbench.core.identity.UserRepository
import doa.ink.workbench.core.permission.AddGroupMemberCommand
import doa.ink.workbench.core.permission.CreatePermissionBindingCommand
import doa.ink.workbench.core.permission.CreatePermissionGroupCommand
import doa.ink.workbench.core.permission.PermissionBindingRecord
import doa.ink.workbench.core.permission.PermissionBindingRepository
import doa.ink.workbench.core.permission.PermissionGroupRecord
import doa.ink.workbench.core.permission.PermissionGroupRepository
import doa.ink.workbench.core.permission.PermissionPolicyRecord
import doa.ink.workbench.core.permission.PermissionPolicyRepository
import doa.ink.workbench.core.permission.PermissionPolicyRuleRecord
import doa.ink.workbench.core.permission.PermissionPrincipalType
import doa.ink.workbench.core.permission.UpdatePermissionGroupCommand
import doa.ink.workbench.core.permission.model.PermissionEffect
import doa.ink.workbench.core.project.ProjectRepository
import doa.ink.workbench.service.common.PublicIdResolver
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service

@Service
@Suppress("TooManyFunctions")
class PermissionManagementService(
  private val groups: PermissionGroupRepository,
  private val policies: PermissionPolicyRepository,
  private val bindings: PermissionBindingRepository,
  private val users: UserRepository,
  private val projects: ProjectRepository,
  private val publicIds: PublicIdResolver,
  private val clock: Clock,
) {
  suspend fun listGroups(tenantId: UUID): List<PermissionGroupView> =
    groups.list(tenantId).map { PermissionGroupView.from(it) }

  suspend fun createGroup(
    tenantId: UUID,
    code: String,
    name: String,
    description: String?,
  ): PermissionGroupView =
    PermissionGroupView.from(
      groups.create(
        CreatePermissionGroupCommand(
          tenantId = tenantId,
          code = code,
          name = name,
          description = description,
        )
      )
    )

  suspend fun getGroup(tenantId: UUID, publicId: String): PermissionGroupView =
    PermissionGroupView.from(requireGroup(tenantId, publicId))

  suspend fun updateGroup(
    tenantId: UUID,
    publicId: String,
    name: String?,
    description: String?,
  ): PermissionGroupView {
    val group = requireGroup(tenantId, publicId)
    if (group.builtin) {
      throw InvalidRequestException("Built-in groups cannot be updated.")
    }
    return PermissionGroupView.from(
      groups.update(UpdatePermissionGroupCommand(group.id, name, description))
    )
  }

  suspend fun deleteGroup(tenantId: UUID, publicId: String): Boolean {
    val group = requireGroup(tenantId, publicId)
    if (group.builtin) {
      throw InvalidRequestException("Built-in groups cannot be deleted.")
    }
    return groups.delete(tenantId, group.id)
  }

  suspend fun listGroupMembers(tenantId: UUID, groupPublicId: String): List<GroupMemberView> {
    val group = requireGroup(tenantId, groupPublicId)
    return groups.listMembers(group.id).map { member ->
      GroupMemberView(
        id = member.apiId.value,
        user = UserSummary.from(requireUser(member.userId)),
      )
    }
  }

  suspend fun addGroupMember(
    tenantId: UUID,
    groupPublicId: String,
    userPublicId: String,
  ): GroupMemberView {
    val group = requireGroup(tenantId, groupPublicId)
    val user = publicIds.resolveUser(userPublicId)
    val member = groups.addMember(AddGroupMemberCommand(groupId = group.id, userId = user.id))
    return GroupMemberView(id = member.apiId.value, user = UserSummary.from(user))
  }

  suspend fun removeGroupMember(
    tenantId: UUID,
    groupPublicId: String,
    userPublicId: String,
  ): Boolean {
    val group = requireGroup(tenantId, groupPublicId)
    val user = publicIds.resolveUser(userPublicId)
    return groups.removeMember(group.id, user.id, OffsetDateTime.now(clock))
  }

  suspend fun listPolicies(tenantId: UUID): List<PermissionPolicyView> =
    policies.list(tenantId).map { policy ->
      PermissionPolicyView.from(policy, policies.listRules(policy.id))
    }

  suspend fun getPolicy(tenantId: UUID, publicId: String): PermissionPolicyView {
    val policy = requirePolicy(tenantId, publicId)
    return PermissionPolicyView.from(policy, policies.listRules(policy.id))
  }

  suspend fun listBindings(tenantId: UUID): List<PermissionBindingView> =
    bindings.listByTenant(tenantId).map { bindingView(tenantId, it) }

  suspend fun createBinding(
    tenantId: UUID,
    principalType: PermissionPrincipalType,
    userPublicId: String?,
    groupPublicId: String?,
    policyPublicId: String,
    projectPublicId: String?,
    effect: PermissionEffect?,
    actorUserId: UUID?,
  ): PermissionBindingView {
    if (effect != null && effect != PermissionEffect.ALLOW) {
      throw InvalidRequestException("Binding effect overrides are not supported; use policy rules.")
    }
    val userId = userPublicId?.let { publicIds.resolveUser(it).id }
    val groupId = groupPublicId?.let { requireGroup(tenantId, it).id }
    val policy = requirePolicy(tenantId, policyPublicId)
    val projectId = projectPublicId?.let { publicIds.resolveProject(tenantId, it).id }
    validatePrincipal(principalType, userId, groupId)
    val binding =
      bindings.create(
        CreatePermissionBindingCommand(
          tenantId = tenantId,
          projectId = projectId,
          principalType = principalType,
          principalUserId = userId,
          principalGroupId = groupId,
          policyId = policy.id,
          validFrom = OffsetDateTime.now(clock),
          createdBy = actorUserId,
        )
      )
    return bindingView(tenantId, binding)
  }

  suspend fun expireBinding(tenantId: UUID, publicId: String): Boolean {
    val binding =
      bindings.findByApiId(tenantId, publicId)
        ?: throw ResourceNotFoundException("Permission binding not found.")
    return bindings.expire(tenantId, binding.id, OffsetDateTime.now(clock))
  }

  @Suppress("ThrowsCount")
  private suspend fun bindingView(
    tenantId: UUID,
    binding: PermissionBindingRecord,
  ): PermissionBindingView {
    val policy =
      policies.findById(tenantId, binding.policyId)
        ?: throw ResourceNotFoundException("Permission policy not found.")
    val user = binding.principalUserId?.let { UserSummary.from(requireUser(it)) }
    val group =
      binding.principalGroupId?.let { id ->
        groups.findById(tenantId, id)?.let { PermissionGroupView.from(it) }
          ?: throw ResourceNotFoundException("Permission group not found.")
      }
    val project =
      binding.projectId?.let { id ->
        projects.findById(tenantId, id)?.let(ProjectSummary::from)
          ?: throw ResourceNotFoundException("Project not found.")
      }
    return PermissionBindingView(
      id = binding.apiId.value,
      principalType = binding.principalType.name,
      user = user,
      group = group,
      policy = PermissionPolicySummary.from(policy),
      project = project,
    )
  }

  private suspend fun requireGroup(tenantId: UUID, publicId: String): PermissionGroupRecord =
    groups.findByApiId(tenantId, publicId)
      ?: throw ResourceNotFoundException("Permission group not found.")

  private suspend fun requirePolicy(tenantId: UUID, publicId: String): PermissionPolicyRecord =
    policies.findByApiId(tenantId, publicId)
      ?: throw ResourceNotFoundException("Permission policy not found.")

  private suspend fun requireUser(userId: UUID) =
    users.findById(userId) ?: throw ResourceNotFoundException("User not found.")

  @Suppress("ThrowsCount")
  private fun validatePrincipal(
    principalType: PermissionPrincipalType,
    userId: UUID?,
    groupId: UUID?,
  ) {
    when (principalType) {
      PermissionPrincipalType.USER ->
        if (userId == null || groupId != null) {
          throw InvalidRequestException("USER binding requires userId only.")
        }
      PermissionPrincipalType.GROUP ->
        if (groupId == null || userId != null) {
          throw InvalidRequestException("GROUP binding requires groupId only.")
        }
      PermissionPrincipalType.TENANT_MEMBER ->
        if (userId != null || groupId != null) {
          throw InvalidRequestException("TENANT_MEMBER binding must not include userId or groupId.")
        }
    }
  }
}

data class PermissionGroupView(
  val id: String,
  val code: String,
  val name: String,
  val description: String?,
  val builtin: Boolean,
) {
  companion object {
    fun from(record: PermissionGroupRecord) =
      PermissionGroupView(
        id = record.apiId.value,
        code = record.code,
        name = record.name,
        description = record.description,
        builtin = record.builtin,
      )
  }
}

data class GroupMemberView(val id: String, val user: UserSummary)

data class PermissionPolicySummary(
  val id: String,
  val code: String,
  val name: String,
) {
  companion object {
    fun from(record: PermissionPolicyRecord) =
      PermissionPolicySummary(id = record.apiId.value, code = record.code, name = record.name)
  }
}

data class PermissionPolicyView(
  val id: String,
  val code: String,
  val name: String,
  val description: String?,
  val builtin: Boolean,
  val rules: List<PermissionPolicyRuleView>,
) {
  companion object {
    fun from(
      record: PermissionPolicyRecord,
      rules: List<PermissionPolicyRuleRecord>,
    ) =
      PermissionPolicyView(
        id = record.apiId.value,
        code = record.code,
        name = record.name,
        description = record.description,
        builtin = record.builtin,
        rules = rules.map { PermissionPolicyRuleView.from(it) },
      )
  }
}

data class PermissionPolicyRuleView(
  val action: String,
  val resourcePattern: String,
  val effect: String,
) {
  companion object {
    fun from(record: PermissionPolicyRuleRecord) =
      PermissionPolicyRuleView(
        action = record.action.code,
        resourcePattern = record.resourcePattern,
        effect = record.effect.name,
      )
  }
}

data class PermissionBindingView(
  val id: String,
  val principalType: String,
  val user: UserSummary?,
  val group: PermissionGroupView?,
  val policy: PermissionPolicySummary,
  val project: ProjectSummary?,
)
