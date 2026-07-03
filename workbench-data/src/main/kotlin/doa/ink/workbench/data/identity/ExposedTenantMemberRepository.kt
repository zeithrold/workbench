package doa.ink.workbench.data.identity

import doa.ink.workbench.core.common.ids.PublicId
import doa.ink.workbench.core.identity.TenantMemberRepository
import doa.ink.workbench.core.identity.model.CreateTenantMemberCommand
import doa.ink.workbench.core.identity.model.TenantMemberRecord
import doa.ink.workbench.data.persistence.TenantMembersTable
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.springframework.stereotype.Repository

@Repository
class ExposedTenantMemberRepository(private val database: Database) : TenantMemberRepository {
  override suspend fun create(command: CreateTenantMemberCommand): TenantMemberRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val now = nowUtc()
      TenantMembersTable.insert {
        it[TenantMembersTable.id] = id.toKotlinUuid()
        it[TenantMembersTable.apiId] = PublicId.new("tmb").value
        it[TenantMembersTable.tenantId] = command.tenantId.toKotlinUuid()
        it[TenantMembersTable.userId] = command.userId.toKotlinUuid()
        it[TenantMembersTable.status] = command.status.dbValue
        it[TenantMembersTable.joinedAt] = command.joinedAt
        it[TenantMembersTable.invitedBy] = command.invitedBy?.toKotlinUuid()
        it[TenantMembersTable.createdAt] = now
        it[TenantMembersTable.updatedAt] = now
      }
      TenantMembersTable.selectAll()
        .where {
          (TenantMembersTable.deletedAt.isNull()) and (TenantMembersTable.id eq id.toKotlinUuid())
        }
        .single()
        .toTenantMemberRecord()
    }

  override suspend fun findByTenantAndUser(tenantId: UUID, userId: UUID): TenantMemberRecord? =
    suspendTransaction(db = database) {
      TenantMembersTable.selectAll()
        .where {
          (TenantMembersTable.deletedAt.isNull()) and
            (TenantMembersTable.tenantId eq tenantId.toKotlinUuid()) and
            (TenantMembersTable.userId eq userId.toKotlinUuid())
        }
        .singleOrNull()
        ?.toTenantMemberRecord()
    }

  override suspend fun listByUser(userId: UUID): List<TenantMemberRecord> =
    suspendTransaction(db = database) {
      TenantMembersTable.selectAll()
        .where {
          (TenantMembersTable.deletedAt.isNull()) and
            (TenantMembersTable.userId eq userId.toKotlinUuid())
        }
        .map { it.toTenantMemberRecord() }
    }
}
