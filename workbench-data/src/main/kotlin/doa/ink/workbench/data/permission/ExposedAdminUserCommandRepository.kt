package doa.ink.workbench.data.permission

import doa.ink.workbench.core.common.ids.PublicId
import doa.ink.workbench.core.permission.AdminUserCommandRepository
import doa.ink.workbench.core.permission.AdminUserRecord
import doa.ink.workbench.core.permission.AdminUserStatus
import doa.ink.workbench.core.permission.CreateAdminUserCommand
import doa.ink.workbench.data.persistence.AdminUsersTable
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

@Repository
class ExposedAdminUserCommandRepository(private val database: Database) :
  AdminUserCommandRepository {
  override suspend fun create(command: CreateAdminUserCommand): AdminUserRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val apiId = PublicId.new("adu")
      val now = AdminRepositoryMappers.nowUtc()
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

  override suspend fun revoke(id: UUID, revokedAt: OffsetDateTime): Boolean =
    suspendTransaction(db = database) {
      AdminUsersTable.update({ AdminUsersTable.id eq id.toKotlinUuid() }) {
        it[status] = AdminUserStatus.REVOKED.dbValue
        it[validTo] = revokedAt
        it[updatedAt] = revokedAt
      } > 0
    }
}
