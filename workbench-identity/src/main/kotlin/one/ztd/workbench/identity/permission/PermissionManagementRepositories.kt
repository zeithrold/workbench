package one.ztd.workbench.identity.permission

import java.time.OffsetDateTime
import java.util.UUID
import one.ztd.workbench.identity.permission.model.AuthorizationAction
import one.ztd.workbench.identity.permission.model.PermissionEffect
import one.ztd.workbench.kernel.common.ids.PublicId

enum class GroupMemberStatus(val dbValue: String) {
  ACTIVE("active"),
  REMOVED("removed"),
}

enum class PermissionPrincipalType(val dbValue: String) {
  USER("user"),
  GROUP("group"),
  TENANT_MEMBER("tenant_member"),
}

data class PermissionGroupRecord(
  val id: UUID,
  val apiId: PublicId,
  val tenantId: UUID,
  val code: String,
  val name: String,
  val description: String?,
  val builtin: Boolean,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)

data class GroupMemberRecord(
  val id: UUID,
  val apiId: PublicId,
  val groupId: UUID,
  val userId: UUID,
  val status: GroupMemberStatus,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)

data class PermissionPolicyRecord(
  val id: UUID,
  val apiId: PublicId,
  val tenantId: UUID,
  val code: String,
  val name: String,
  val description: String?,
  val builtin: Boolean,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)

data class PermissionPolicyRuleRecord(
  val id: UUID,
  val apiId: PublicId,
  val policyId: UUID,
  val action: AuthorizationAction,
  val resourcePattern: String,
  val effect: PermissionEffect,
  val conditionJson: String?,
  val position: Int = 0,
  val createdAt: OffsetDateTime,
)

data class PermissionBindingRecord(
  val id: UUID,
  val apiId: PublicId,
  val tenantId: UUID,
  val projectId: UUID?,
  val principalType: PermissionPrincipalType,
  val principalUserId: UUID?,
  val principalGroupId: UUID?,
  val policyId: UUID,
  val validFrom: OffsetDateTime,
  val validTo: OffsetDateTime?,
  val createdBy: UUID?,
  val createdAt: OffsetDateTime,
)

data class ResolvedPermissionRule(
  val bindingId: UUID,
  val action: AuthorizationAction,
  val resourcePattern: String,
  val effect: PermissionEffect,
  val conditionJson: String? = null,
)

data class CreatePermissionGroupCommand(
  val tenantId: UUID,
  val code: String,
  val name: String,
  val description: String?,
  val builtin: Boolean = false,
)

data class UpdatePermissionGroupCommand(
  val groupId: UUID,
  val name: String?,
  val description: String?,
)

data class AddGroupMemberCommand(
  val groupId: UUID,
  val userId: UUID,
)

data class CreatePermissionPolicyCommand(
  val tenantId: UUID,
  val code: String,
  val name: String,
  val description: String?,
  val builtin: Boolean = false,
  val rules: List<CreatePermissionPolicyRuleCommand> = emptyList(),
)

data class UpdatePermissionPolicyCommand(
  val policyId: UUID,
  val name: String?,
  val description: String?,
)

data class CreatePermissionPolicyRuleCommand(
  val policyId: UUID,
  val action: AuthorizationAction,
  val resourcePattern: String,
  val effect: PermissionEffect = PermissionEffect.ALLOW,
  val conditionJson: String? = null,
  val position: Int = 0,
)

data class ReplacePermissionPolicyRuleCommand(
  val apiId: String?,
  val action: AuthorizationAction,
  val resourcePattern: String,
  val effect: PermissionEffect,
  val conditionJson: String?,
  val position: Int,
)

data class ReplacePermissionPolicyCommand(
  val policyId: UUID,
  val expectedUpdatedAt: OffsetDateTime,
  val name: String,
  val description: String?,
  val rules: List<ReplacePermissionPolicyRuleCommand>,
)

data class CreatePermissionBindingCommand(
  val tenantId: UUID,
  val projectId: UUID?,
  val principalType: PermissionPrincipalType,
  val principalUserId: UUID?,
  val principalGroupId: UUID?,
  val policyId: UUID,
  val validFrom: OffsetDateTime,
  val validTo: OffsetDateTime? = null,
  val createdBy: UUID?,
)

interface PermissionGroupRepository {
  suspend fun create(command: CreatePermissionGroupCommand): PermissionGroupRecord

  suspend fun update(command: UpdatePermissionGroupCommand): PermissionGroupRecord

  suspend fun findById(tenantId: UUID, id: UUID): PermissionGroupRecord?

  suspend fun findByApiId(tenantId: UUID, apiId: String): PermissionGroupRecord?

  suspend fun findByCode(tenantId: UUID, code: String): PermissionGroupRecord?

  suspend fun list(tenantId: UUID): List<PermissionGroupRecord>

  suspend fun delete(tenantId: UUID, id: UUID): Boolean

  suspend fun addMember(command: AddGroupMemberCommand): GroupMemberRecord

  suspend fun removeMember(groupId: UUID, userId: UUID, removedAt: OffsetDateTime): Boolean

  suspend fun listMembers(groupId: UUID): List<GroupMemberRecord>

  suspend fun listActiveGroupIdsForUser(tenantId: UUID, userId: UUID): Set<UUID>
}

interface PermissionPolicyRepository {
  suspend fun create(command: CreatePermissionPolicyCommand): PermissionPolicyRecord

  suspend fun findById(tenantId: UUID, id: UUID): PermissionPolicyRecord?

  suspend fun findByApiId(tenantId: UUID, apiId: String): PermissionPolicyRecord?

  suspend fun findByCode(tenantId: UUID, code: String): PermissionPolicyRecord?

  suspend fun list(tenantId: UUID): List<PermissionPolicyRecord>

  suspend fun update(command: UpdatePermissionPolicyCommand): PermissionPolicyRecord

  suspend fun delete(tenantId: UUID, id: UUID): Boolean

  suspend fun addRule(command: CreatePermissionPolicyRuleCommand): PermissionPolicyRuleRecord

  /** Returns null when the optimistic revision no longer matches. */
  suspend fun replace(command: ReplacePermissionPolicyCommand): PermissionPolicyRecord?

  suspend fun listRules(policyId: UUID): List<PermissionPolicyRuleRecord>

  suspend fun hasActiveBindings(policyId: UUID, at: OffsetDateTime): Boolean
}

interface PermissionBindingRepository {
  suspend fun create(command: CreatePermissionBindingCommand): PermissionBindingRecord

  suspend fun findByApiId(tenantId: UUID, apiId: String): PermissionBindingRecord?

  suspend fun listByTenant(tenantId: UUID): List<PermissionBindingRecord>

  suspend fun listByProject(tenantId: UUID, projectId: UUID): List<PermissionBindingRecord>

  suspend fun listProjectIdsForSubject(
    tenantId: UUID,
    subjectUserId: UUID,
    at: OffsetDateTime,
  ): Set<UUID>

  suspend fun expire(tenantId: UUID, id: UUID, validTo: OffsetDateTime): Boolean

  suspend fun expireByProject(
    tenantId: UUID,
    projectId: UUID,
    validTo: OffsetDateTime,
  ): Int

  suspend fun listActiveRulesForSubject(
    subjectUserId: UUID,
    tenantId: UUID,
    projectId: UUID?,
    at: OffsetDateTime,
  ): List<ResolvedPermissionRule>
}
