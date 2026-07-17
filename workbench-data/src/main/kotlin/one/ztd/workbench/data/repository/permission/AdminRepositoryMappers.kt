package one.ztd.workbench.data.repository.permission

import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.uuid.toJavaUuid
import one.ztd.workbench.data.persistence.postgres.permission.AccessGrantsTable
import one.ztd.workbench.data.persistence.postgres.permission.AdminUsersTable
import one.ztd.workbench.data.persistence.postgres.permission.GroupMembersTable
import one.ztd.workbench.data.persistence.postgres.permission.GroupsTable
import one.ztd.workbench.data.persistence.postgres.permission.PermissionBindingsTable
import one.ztd.workbench.data.persistence.postgres.permission.PermissionPoliciesTable
import one.ztd.workbench.data.persistence.postgres.permission.PermissionPolicyRulesTable
import one.ztd.workbench.identity.permission.AccessGrantRecord
import one.ztd.workbench.identity.permission.AdminUserRecord
import one.ztd.workbench.identity.permission.GroupMemberRecord
import one.ztd.workbench.identity.permission.PermissionBindingRecord
import one.ztd.workbench.identity.permission.PermissionGroupRecord
import one.ztd.workbench.identity.permission.PermissionPolicyRecord
import one.ztd.workbench.identity.permission.PermissionPolicyRuleRecord
import one.ztd.workbench.identity.permission.model.AuthorizationAction
import one.ztd.workbench.identity.permission.model.PermissionEffect
import one.ztd.workbench.kernel.common.ids.PublicId
import org.jetbrains.exposed.v1.core.ResultRow

internal object AdminRepositoryMappers {
  fun nowUtc(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)
}

internal val PermissionEffect.dbValue: String
  get() = name.lowercase()

internal fun ResultRow.toAdminUserRecord() =
  AdminUserRecord(
    id = this[AdminUsersTable.id].toJavaUuid(),
    apiId = PublicId(this[AdminUsersTable.apiId]),
    userId = this[AdminUsersTable.userId].toJavaUuid(),
    scope = adminScopeOf(this[AdminUsersTable.scope]),
    tenantId = this[AdminUsersTable.tenantId]?.toJavaUuid(),
    status = adminUserStatusOf(this[AdminUsersTable.status]),
    grantedBy = this[AdminUsersTable.grantedBy]?.toJavaUuid(),
    validFrom = this[AdminUsersTable.validFrom],
    validTo = this[AdminUsersTable.validTo],
    createdAt = this[AdminUsersTable.createdAt],
    updatedAt = this[AdminUsersTable.updatedAt],
  )

internal fun ResultRow.toAccessGrantRecord() =
  AccessGrantRecord(
    id = this[AccessGrantsTable.id].toJavaUuid(),
    apiId = PublicId(this[AccessGrantsTable.apiId]),
    scope = grantScopeOf(this[AccessGrantsTable.scope]),
    tenantId = this[AccessGrantsTable.tenantId]?.toJavaUuid(),
    projectId = this[AccessGrantsTable.projectId]?.toJavaUuid(),
    subjectUserId = this[AccessGrantsTable.subjectUserId].toJavaUuid(),
    action = AuthorizationAction(this[AccessGrantsTable.action]),
    resourcePattern = this[AccessGrantsTable.resourcePattern],
    effect = permissionEffectOf(this[AccessGrantsTable.effect]),
    validFrom = this[AccessGrantsTable.validFrom],
    validTo = this[AccessGrantsTable.validTo],
    grantedBy = this[AccessGrantsTable.grantedBy]?.toJavaUuid(),
    createdAt = this[AccessGrantsTable.createdAt],
  )

internal fun ResultRow.toPermissionGroupRecord() =
  PermissionGroupRecord(
    id = this[GroupsTable.id].toJavaUuid(),
    apiId = PublicId(this[GroupsTable.apiId]),
    tenantId = this[GroupsTable.tenantId].toJavaUuid(),
    code = this[GroupsTable.code],
    name = this[GroupsTable.name],
    description = this[GroupsTable.description],
    builtin = this[GroupsTable.builtin],
    createdAt = this[GroupsTable.createdAt],
    updatedAt = this[GroupsTable.updatedAt],
  )

internal fun ResultRow.toGroupMemberRecord() =
  GroupMemberRecord(
    id = this[GroupMembersTable.id].toJavaUuid(),
    apiId = PublicId(this[GroupMembersTable.apiId]),
    groupId = this[GroupMembersTable.groupId].toJavaUuid(),
    userId = this[GroupMembersTable.userId].toJavaUuid(),
    status = groupMemberStatusOf(this[GroupMembersTable.status]),
    createdAt = this[GroupMembersTable.createdAt],
    updatedAt = this[GroupMembersTable.updatedAt],
  )

internal fun ResultRow.toPermissionPolicyRecord() =
  PermissionPolicyRecord(
    id = this[PermissionPoliciesTable.id].toJavaUuid(),
    apiId = PublicId(this[PermissionPoliciesTable.apiId]),
    tenantId = this[PermissionPoliciesTable.tenantId].toJavaUuid(),
    code = this[PermissionPoliciesTable.code],
    name = this[PermissionPoliciesTable.name],
    description = this[PermissionPoliciesTable.description],
    builtin = this[PermissionPoliciesTable.builtin],
    createdAt = this[PermissionPoliciesTable.createdAt],
    updatedAt = this[PermissionPoliciesTable.updatedAt],
  )

internal fun ResultRow.toPermissionPolicyRuleRecord() =
  PermissionPolicyRuleRecord(
    id = this[PermissionPolicyRulesTable.id].toJavaUuid(),
    apiId = PublicId(this[PermissionPolicyRulesTable.apiId]),
    policyId = this[PermissionPolicyRulesTable.policyId].toJavaUuid(),
    action = AuthorizationAction(this[PermissionPolicyRulesTable.action]),
    resourcePattern = this[PermissionPolicyRulesTable.resourcePattern],
    effect = permissionEffectOf(this[PermissionPolicyRulesTable.effect]),
    conditionJson = this[PermissionPolicyRulesTable.conditionJson],
    position = this[PermissionPolicyRulesTable.position],
    createdAt = this[PermissionPolicyRulesTable.createdAt],
  )

internal fun ResultRow.toPermissionBindingRecord() =
  PermissionBindingRecord(
    id = this[PermissionBindingsTable.id].toJavaUuid(),
    apiId = PublicId(this[PermissionBindingsTable.apiId]),
    tenantId = this[PermissionBindingsTable.tenantId].toJavaUuid(),
    projectId = this[PermissionBindingsTable.projectId]?.toJavaUuid(),
    principalType = permissionPrincipalTypeOf(this[PermissionBindingsTable.principalType]),
    principalUserId = this[PermissionBindingsTable.principalUserId]?.toJavaUuid(),
    principalGroupId = this[PermissionBindingsTable.principalGroupId]?.toJavaUuid(),
    policyId = this[PermissionBindingsTable.policyId].toJavaUuid(),
    validFrom = this[PermissionBindingsTable.validFrom],
    validTo = this[PermissionBindingsTable.validTo],
    createdBy = this[PermissionBindingsTable.createdBy]?.toJavaUuid(),
    createdAt = this[PermissionBindingsTable.createdAt],
  )
