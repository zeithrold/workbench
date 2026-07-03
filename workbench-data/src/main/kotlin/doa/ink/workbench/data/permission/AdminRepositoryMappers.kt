package doa.ink.workbench.data.permission

import doa.ink.workbench.core.common.ids.PublicId
import doa.ink.workbench.core.permission.AccessGrantRecord
import doa.ink.workbench.core.permission.AdminScope
import doa.ink.workbench.core.permission.AdminUserRecord
import doa.ink.workbench.core.permission.AdminUserStatus
import doa.ink.workbench.core.permission.GrantScope
import doa.ink.workbench.core.permission.model.AuthorizationAction
import doa.ink.workbench.core.permission.model.PermissionEffect
import doa.ink.workbench.data.persistence.AccessGrantsTable
import doa.ink.workbench.data.persistence.AdminUsersTable
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
