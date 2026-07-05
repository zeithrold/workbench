@file:Suppress("TooManyFunctions")

package ink.doa.workbench.data.repository.permission

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.permission.AccessGrantRecord
import ink.doa.workbench.core.permission.AdminScope
import ink.doa.workbench.core.permission.AdminUserRecord
import ink.doa.workbench.core.permission.AdminUserStatus
import ink.doa.workbench.core.permission.GrantScope
import ink.doa.workbench.core.permission.GroupMemberRecord
import ink.doa.workbench.core.permission.GroupMemberStatus
import ink.doa.workbench.core.permission.PermissionBindingRecord
import ink.doa.workbench.core.permission.PermissionGroupRecord
import ink.doa.workbench.core.permission.PermissionPolicyRecord
import ink.doa.workbench.core.permission.PermissionPolicyRuleRecord
import ink.doa.workbench.core.permission.PermissionPrincipalType
import ink.doa.workbench.core.permission.model.AuthorizationAction
import ink.doa.workbench.core.permission.model.PermissionEffect
import ink.doa.workbench.data.persistence.postgres.permission.AccessGrantsTable
import ink.doa.workbench.data.persistence.postgres.permission.AdminUsersTable
import ink.doa.workbench.data.persistence.postgres.permission.GroupMembersTable
import ink.doa.workbench.data.persistence.postgres.permission.GroupsTable
import ink.doa.workbench.data.persistence.postgres.permission.PermissionBindingsTable
import ink.doa.workbench.data.persistence.postgres.permission.PermissionPoliciesTable
import ink.doa.workbench.data.persistence.postgres.permission.PermissionPolicyRulesTable
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.uuid.toJavaUuid
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

internal fun adminScopeOf(value: String): AdminScope =
  AdminScope.entries.first { it.dbValue == value }

internal fun adminUserStatusOf(value: String): AdminUserStatus =
  AdminUserStatus.entries.first { it.dbValue == value }

internal fun grantScopeOf(value: String): GrantScope =
  GrantScope.entries.first { it.dbValue == value }

internal fun permissionEffectOf(value: String): PermissionEffect =
  when (value.lowercase()) {
    "allow" -> PermissionEffect.ALLOW
    "deny" -> PermissionEffect.DENY
    else -> error("Unknown permission effect: $value")
  }

internal fun groupMemberStatusOf(value: String): GroupMemberStatus =
  GroupMemberStatus.entries.first { it.dbValue == value }

internal fun permissionPrincipalTypeOf(value: String): PermissionPrincipalType =
  PermissionPrincipalType.entries.first { it.dbValue == value }
