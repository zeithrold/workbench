package doa.ink.workbench.data.permission

import doa.ink.workbench.core.common.ids.PublicId
import doa.ink.workbench.core.permission.AccessGrantRecord
import doa.ink.workbench.core.permission.AccessGrantRepository
import doa.ink.workbench.core.permission.AdminScope
import doa.ink.workbench.core.permission.AdminUserRecord
import doa.ink.workbench.core.permission.AdminUserRepository
import doa.ink.workbench.core.permission.AdminUserStatus
import doa.ink.workbench.core.permission.CreateAccessGrantCommand
import doa.ink.workbench.core.permission.CreateAdminUserCommand
import doa.ink.workbench.core.permission.GrantScope
import doa.ink.workbench.core.permission.model.AuthorizationAction
import doa.ink.workbench.core.permission.model.PermissionEffect
import doa.ink.workbench.data.persistence.AccessGrantsTable
import doa.ink.workbench.data.persistence.AdminUsersTable
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

@Repository
@Suppress("TooManyFunctions")
class ExposedAdminUserRepository(private val database: Database) : AdminUserRepository {
  override suspend fun create(command: CreateAdminUserCommand): AdminUserRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val apiId = PublicId.new("adu")
      val now = nowUtc()
      AdminUsersTable.insert {
        it[AdminUsersTable.id] = id.toKotlinUuid()
        it[AdminUsersTable.apiId] = apiId.value
        it[userId] = command.userId.toKotlinUuid()
        it[scope] = command.scope.dbValue
        it[tenantId] = command.tenantId?.toKotlinUuid()
        it[status] = AdminUserStatus.ACTIVE.dbValue
        it[grantedBy] = command.grantedBy?.toKotlinUuid()
        it[validFrom] = command.validFrom
        it[validTo] = command.validTo
        it[createdAt] = now
        it[updatedAt] = now
      }
      AdminUsersTable.selectAll()
        .where { AdminUsersTable.id eq id.toKotlinUuid() }
        .single()
        .toAdminUserRecord()
    }

  override suspend fun findById(id: UUID): AdminUserRecord? =
    suspendTransaction(db = database) {
      AdminUsersTable.selectAll()
        .where { AdminUsersTable.id eq id.toKotlinUuid() }
        .singleOrNull()
        ?.toAdminUserRecord()
    }

  override suspend fun findByApiId(apiId: String): AdminUserRecord? =
    suspendTransaction(db = database) {
      AdminUsersTable.selectAll()
        .where { AdminUsersTable.apiId eq apiId }
        .singleOrNull()
        ?.toAdminUserRecord()
    }

  override suspend fun findActiveInstanceAdmin(userId: UUID, at: OffsetDateTime): AdminUserRecord? =
    suspendTransaction(db = database) {
      AdminUsersTable.selectAll()
        .where {
          (AdminUsersTable.userId eq userId.toKotlinUuid()) and
            (AdminUsersTable.scope eq AdminScope.INSTANCE.dbValue) and
            (AdminUsersTable.status eq AdminUserStatus.ACTIVE.dbValue) and
            (AdminUsersTable.validFrom lessEq at) and
            (AdminUsersTable.validTo.isNull() or (AdminUsersTable.validTo greater at))
        }
        .singleOrNull()
        ?.toAdminUserRecord()
    }

  override suspend fun findActiveTenantAdmin(
    tenantId: UUID,
    userId: UUID,
    at: OffsetDateTime,
  ): AdminUserRecord? =
    suspendTransaction(db = database) {
      AdminUsersTable.selectAll()
        .where {
          (AdminUsersTable.tenantId eq tenantId.toKotlinUuid()) and
            (AdminUsersTable.userId eq userId.toKotlinUuid()) and
            (AdminUsersTable.scope eq AdminScope.TENANT.dbValue) and
            (AdminUsersTable.status eq AdminUserStatus.ACTIVE.dbValue) and
            (AdminUsersTable.validFrom lessEq at) and
            (AdminUsersTable.validTo.isNull() or (AdminUsersTable.validTo greater at))
        }
        .singleOrNull()
        ?.toAdminUserRecord()
    }

  override suspend fun existsActiveInstanceAdmin(): Boolean =
    suspendTransaction(db = database) {
      AdminUsersTable.selectAll()
        .where {
          (AdminUsersTable.scope eq AdminScope.INSTANCE.dbValue) and
            (AdminUsersTable.status eq AdminUserStatus.ACTIVE.dbValue) and
            AdminUsersTable.validTo.isNull()
        }
        .limit(1)
        .any()
    }

  override suspend fun isActiveInstanceAdmin(userId: UUID, at: OffsetDateTime): Boolean =
    findActiveInstanceAdmin(userId, at) != null

  override suspend fun isActiveTenantAdmin(
    tenantId: UUID,
    userId: UUID,
    at: OffsetDateTime,
  ): Boolean = findActiveTenantAdmin(tenantId, userId, at) != null

  override suspend fun listByUser(userId: UUID): List<AdminUserRecord> =
    suspendTransaction(db = database) {
      AdminUsersTable.selectAll()
        .where { AdminUsersTable.userId eq userId.toKotlinUuid() }
        .orderBy(AdminUsersTable.createdAt to SortOrder.DESC)
        .map { it.toAdminUserRecord() }
    }

  override suspend fun listInstanceAdmins(): List<AdminUserRecord> =
    suspendTransaction(db = database) {
      AdminUsersTable.selectAll()
        .where { AdminUsersTable.scope eq AdminScope.INSTANCE.dbValue }
        .orderBy(AdminUsersTable.createdAt to SortOrder.ASC)
        .map { it.toAdminUserRecord() }
    }

  override suspend fun listTenantAdmins(tenantId: UUID): List<AdminUserRecord> =
    suspendTransaction(db = database) {
      AdminUsersTable.selectAll()
        .where {
          (AdminUsersTable.tenantId eq tenantId.toKotlinUuid()) and
            (AdminUsersTable.scope eq AdminScope.TENANT.dbValue)
        }
        .orderBy(AdminUsersTable.createdAt to SortOrder.ASC)
        .map { it.toAdminUserRecord() }
    }

  override suspend fun revoke(id: UUID, revokedAt: OffsetDateTime): Boolean =
    suspendTransaction(db = database) {
      AdminUsersTable.update({ AdminUsersTable.id eq id.toKotlinUuid() }) {
        it[status] = AdminUserStatus.REVOKED.dbValue
        it[validTo] = revokedAt
        it[updatedAt] = revokedAt
      } > 0
    }
}

@Repository
class ExposedAccessGrantRepository(private val database: Database) : AccessGrantRepository {
  override suspend fun create(command: CreateAccessGrantCommand): AccessGrantRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val apiId = PublicId.new("agr")
      val now = nowUtc()
      AccessGrantsTable.insert {
        it[AccessGrantsTable.id] = id.toKotlinUuid()
        it[AccessGrantsTable.apiId] = apiId.value
        it[scope] = command.scope.dbValue
        it[tenantId] = command.tenantId?.toKotlinUuid()
        it[projectId] = command.projectId?.toKotlinUuid()
        it[subjectUserId] = command.subjectUserId.toKotlinUuid()
        it[action] = command.action.code
        it[resourcePattern] = command.resourcePattern
        it[effect] = command.effect.dbValue
        it[validFrom] = command.validFrom
        it[validTo] = command.validTo
        it[grantedBy] = command.grantedBy?.toKotlinUuid()
        it[createdAt] = now
      }
      AccessGrantsTable.selectAll()
        .where { AccessGrantsTable.id eq id.toKotlinUuid() }
        .single()
        .toAccessGrantRecord()
    }

  override suspend fun findById(id: UUID): AccessGrantRecord? =
    suspendTransaction(db = database) {
      AccessGrantsTable.selectAll()
        .where { AccessGrantsTable.id eq id.toKotlinUuid() }
        .singleOrNull()
        ?.toAccessGrantRecord()
    }

  override suspend fun findByApiId(apiId: String): AccessGrantRecord? =
    suspendTransaction(db = database) {
      AccessGrantsTable.selectAll()
        .where { AccessGrantsTable.apiId eq apiId }
        .singleOrNull()
        ?.toAccessGrantRecord()
    }

  override suspend fun listBySubject(
    subjectUserId: UUID,
    scope: GrantScope?,
    tenantId: UUID?,
    projectId: UUID?,
  ): List<AccessGrantRecord> =
    suspendTransaction(db = database) {
      AccessGrantsTable.selectAll()
        .where {
          (AccessGrantsTable.subjectUserId eq subjectUserId.toKotlinUuid()) and
            (scope?.let { AccessGrantsTable.scope eq it.dbValue }
              ?: org.jetbrains.exposed.v1.core.Op.TRUE) and
            (tenantId?.let { AccessGrantsTable.tenantId eq it.toKotlinUuid() }
              ?: org.jetbrains.exposed.v1.core.Op.TRUE) and
            (projectId?.let { AccessGrantsTable.projectId eq it.toKotlinUuid() }
              ?: org.jetbrains.exposed.v1.core.Op.TRUE)
        }
        .orderBy(AccessGrantsTable.createdAt to SortOrder.DESC)
        .map { it.toAccessGrantRecord() }
    }

  override suspend fun listActiveForSubject(
    subjectUserId: UUID,
    scope: GrantScope,
    tenantId: UUID?,
    projectId: UUID?,
    at: OffsetDateTime,
  ): List<AccessGrantRecord> =
    suspendTransaction(db = database) {
      val projectScope =
        when {
          scope == GrantScope.PROJECT && projectId != null ->
            AccessGrantsTable.projectId.isNull() or
              (AccessGrantsTable.projectId eq projectId.toKotlinUuid())
          scope != GrantScope.INSTANCE -> AccessGrantsTable.projectId.isNull()
          else -> org.jetbrains.exposed.v1.core.Op.TRUE
        }
      AccessGrantsTable.selectAll()
        .where {
          (AccessGrantsTable.subjectUserId eq subjectUserId.toKotlinUuid()) and
            (AccessGrantsTable.scope eq scope.dbValue) and
            (tenantId?.let { AccessGrantsTable.tenantId eq it.toKotlinUuid() }
              ?: AccessGrantsTable.tenantId.isNull()) and
            projectScope and
            (AccessGrantsTable.validFrom lessEq at) and
            (AccessGrantsTable.validTo.isNull() or (AccessGrantsTable.validTo greater at))
        }
        .map { it.toAccessGrantRecord() }
    }

  override suspend fun listByTenant(tenantId: UUID): List<AccessGrantRecord> =
    suspendTransaction(db = database) {
      AccessGrantsTable.selectAll()
        .where { AccessGrantsTable.tenantId eq tenantId.toKotlinUuid() }
        .orderBy(AccessGrantsTable.createdAt to SortOrder.DESC)
        .map { it.toAccessGrantRecord() }
    }

  override suspend fun listInstanceGrants(): List<AccessGrantRecord> =
    suspendTransaction(db = database) {
      AccessGrantsTable.selectAll()
        .where { AccessGrantsTable.scope eq GrantScope.INSTANCE.dbValue }
        .orderBy(AccessGrantsTable.createdAt to SortOrder.DESC)
        .map { it.toAccessGrantRecord() }
    }

  override suspend fun expire(id: UUID, validTo: OffsetDateTime): Boolean =
    suspendTransaction(db = database) {
      AccessGrantsTable.update({ AccessGrantsTable.id eq id.toKotlinUuid() }) {
        it[AccessGrantsTable.validTo] = validTo
      } > 0
    }
}

private val PermissionEffect.dbValue: String
  get() = name.lowercase()

private fun ResultRow.toAdminUserRecord() =
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

private fun ResultRow.toAccessGrantRecord() =
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

private fun adminScopeOf(value: String): AdminScope =
  AdminScope.entries.first { it.dbValue == value }

private fun adminUserStatusOf(value: String): AdminUserStatus =
  AdminUserStatus.entries.first { it.dbValue == value }

private fun grantScopeOf(value: String): GrantScope =
  GrantScope.entries.first { it.dbValue == value }

private fun permissionEffectOf(value: String): PermissionEffect =
  when (value.lowercase()) {
    "allow" -> PermissionEffect.ALLOW
    "deny" -> PermissionEffect.DENY
    else -> error("Unknown permission effect: $value")
  }

private fun nowUtc(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)
