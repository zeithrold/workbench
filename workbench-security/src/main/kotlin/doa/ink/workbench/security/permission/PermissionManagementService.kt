package doa.ink.workbench.security.permission

import doa.ink.workbench.core.common.errors.InvalidRequestException
import doa.ink.workbench.core.common.errors.ResourceNotFoundException
import doa.ink.workbench.core.common.errors.WorkbenchErrorCode
import doa.ink.workbench.core.common.summary.ProjectSummary
import doa.ink.workbench.core.common.summary.UserSummary
import doa.ink.workbench.core.identity.UserRepository
import doa.ink.workbench.core.permission.AddGroupMemberCommand
import doa.ink.workbench.core.permission.CreatePermissionBindingCommand
import doa.ink.workbench.core.permission.CreatePermissionGroupCommand
import doa.ink.workbench.core.permission.CreatePermissionPolicyCommand
import doa.ink.workbench.core.permission.CreatePermissionPolicyRuleCommand
import doa.ink.workbench.core.permission.PermissionBindingRecord
import doa.ink.workbench.core.permission.PermissionBindingRepository
import doa.ink.workbench.core.permission.PermissionGroupRecord
import doa.ink.workbench.core.permission.PermissionGroupRepository
import doa.ink.workbench.core.permission.PermissionPolicyRecord
import doa.ink.workbench.core.permission.PermissionPolicyRepository
import doa.ink.workbench.core.permission.PermissionPolicyRuleRecord
import doa.ink.workbench.core.permission.PermissionPrincipalType
import doa.ink.workbench.core.permission.UpdatePermissionGroupCommand
import doa.ink.workbench.core.permission.UpdatePermissionPolicyCommand
import doa.ink.workbench.core.permission.model.AuthorizationAction
import doa.ink.workbench.core.permission.model.PermissionEffect
import doa.ink.workbench.core.project.ProjectRepository
import doa.ink.workbench.security.common.PublicIdResolver
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
      throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_GROUP_BUILTIN_UPDATE_FORBIDDEN)
    }
    return PermissionGroupView.from(
      groups.update(UpdatePermissionGroupCommand(group.id, name, description))
    )
  }

  suspend fun deleteGroup(tenantId: UUID, publicId: String): Boolean {
    val group = requireGroup(tenantId, publicId)
    if (group.builtin) {
      throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_GROUP_BUILTIN_DELETE_FORBIDDEN)
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

  suspend fun createPolicy(
    tenantId: UUID,
    code: String,
    name: String,
    description: String?,
  ): PermissionPolicyView {
    if (policies.findByCode(tenantId, code) != null) {
      throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_POLICY_CODE_IN_USE)
    }
    val policy =
      policies.create(
        CreatePermissionPolicyCommand(
          tenantId = tenantId,
          code = code,
          name = name,
          description = description,
          builtin = false,
        )
      )
    return PermissionPolicyView.from(policy, emptyList())
  }

  suspend fun updatePolicy(
    tenantId: UUID,
    publicId: String,
    name: String?,
    description: String?,
  ): PermissionPolicyView {
    val policy = requirePolicy(tenantId, publicId)
    if (policy.builtin) {
      throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_POLICY_BUILTIN_UPDATE_FORBIDDEN)
    }
    return PermissionPolicyView.from(
      policies.update(UpdatePermissionPolicyCommand(policy.id, name, description)),
      policies.listRules(policy.id),
    )
  }

  suspend fun deletePolicy(tenantId: UUID, publicId: String): Boolean {
    val policy = requirePolicy(tenantId, publicId)
    if (policy.builtin) {
      throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_POLICY_BUILTIN_DELETE_FORBIDDEN)
    }
    if (policies.hasActiveBindings(policy.id, OffsetDateTime.now(clock))) {
      throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_POLICY_ACTIVE_BINDINGS)
    }
    return policies.delete(tenantId, policy.id)
  }

  suspend fun addPolicyRule(
    tenantId: UUID,
    policyPublicId: String,
    action: String,
    resourcePattern: String,
    effect: PermissionEffect,
  ): PermissionPolicyView {
    val policy = requirePolicy(tenantId, policyPublicId)
    if (policy.builtin) {
      throw InvalidRequestException(
        WorkbenchErrorCode.PERMISSION_POLICY_RULES_BUILTIN_CHANGE_FORBIDDEN
      )
    }
    policies.addRule(
      CreatePermissionPolicyRuleCommand(
        policyId = policy.id,
        action = AuthorizationAction(action),
        resourcePattern = resourcePattern,
        effect = effect,
      )
    )
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
      throw InvalidRequestException(
        WorkbenchErrorCode.PERMISSION_BINDING_EFFECT_OVERRIDE_UNSUPPORTED
      )
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
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PERMISSION_BINDING_NOT_FOUND)
    return bindings.expire(tenantId, binding.id, OffsetDateTime.now(clock))
  }

  @Suppress("ThrowsCount")
  private suspend fun bindingView(
    tenantId: UUID,
    binding: PermissionBindingRecord,
  ): PermissionBindingView {
    val policy =
      policies.findById(tenantId, binding.policyId)
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PERMISSION_POLICY_NOT_FOUND)
    val user = binding.principalUserId?.let { UserSummary.from(requireUser(it)) }
    val group =
      binding.principalGroupId?.let { id ->
        groups.findById(tenantId, id)?.let { PermissionGroupView.from(it) }
          ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PERMISSION_GROUP_NOT_FOUND)
      }
    val project =
      binding.projectId?.let { id ->
        projects.findById(tenantId, id)?.let(ProjectSummary::from)
          ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PROJECT_NOT_FOUND)
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
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PERMISSION_GROUP_NOT_FOUND)

  private suspend fun requirePolicy(tenantId: UUID, publicId: String): PermissionPolicyRecord =
    policies.findByApiId(tenantId, publicId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PERMISSION_POLICY_NOT_FOUND)

  private suspend fun requireUser(userId: UUID) =
    users.findById(userId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND)

  @Suppress("ThrowsCount")
  private fun validatePrincipal(
    principalType: PermissionPrincipalType,
    userId: UUID?,
    groupId: UUID?,
  ) {
    when (principalType) {
      PermissionPrincipalType.USER ->
        if (userId == null || groupId != null) {
          throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_BINDING_USER_TARGET_INVALID)
        }
      PermissionPrincipalType.GROUP ->
        if (groupId == null || userId != null) {
          throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_BINDING_GROUP_TARGET_INVALID)
        }
      PermissionPrincipalType.TENANT_MEMBER ->
        if (userId != null || groupId != null) {
          throw InvalidRequestException(
            WorkbenchErrorCode.PERMISSION_BINDING_TENANT_MEMBER_TARGET_INVALID
          )
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
