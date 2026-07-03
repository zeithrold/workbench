package doa.ink.workbench.data.permission

import doa.ink.workbench.core.permission.AdminScope
import doa.ink.workbench.core.permission.AdminUserQueryRepository
import doa.ink.workbench.core.permission.AdminUserRecord
import doa.ink.workbench.core.permission.AdminUserStatus
import doa.ink.workbench.data.persistence.AdminUsersTable
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.springframework.stereotype.Repository

@Repository
class ExposedAdminUserQueryRepository(private val database: Database) : AdminUserQueryRepository {
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
}
