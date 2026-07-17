package one.ztd.workbench.data.repository.permission

import one.ztd.workbench.identity.permission.AdminScope
import one.ztd.workbench.identity.permission.AdminUserStatus
import one.ztd.workbench.identity.permission.GrantScope
import one.ztd.workbench.identity.permission.GroupMemberStatus
import one.ztd.workbench.identity.permission.PermissionPrincipalType
import one.ztd.workbench.identity.permission.model.PermissionEffect

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
