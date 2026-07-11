package ink.doa.workbench.data.repository.permission

import ink.doa.workbench.core.permission.AdminScope
import ink.doa.workbench.core.permission.AdminUserStatus
import ink.doa.workbench.core.permission.GrantScope
import ink.doa.workbench.core.permission.GroupMemberStatus
import ink.doa.workbench.core.permission.PermissionPrincipalType
import ink.doa.workbench.core.permission.model.PermissionEffect

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
