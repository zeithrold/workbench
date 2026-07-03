package ink.doa.workbench.data.permission

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.permission.AccessGrantRecord
import ink.doa.workbench.core.permission.AccessGrantRepository
import ink.doa.workbench.core.permission.CreateAccessGrantCommand
import ink.doa.workbench.core.permission.GrantScope
import ink.doa.workbench.data.persistence.AccessGrantsTable
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
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

@Repository
class ExposedAccessGrantRepository(private val database: Database) : AccessGrantRepository {
  override suspend fun create(command: CreateAccessGrantCommand): AccessGrantRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val apiId = PublicId.new("agr")
      val now = AdminRepositoryMappers.nowUtc()
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

  override suspend fun expireByTenant(tenantId: UUID, expiredAt: OffsetDateTime): Int =
    suspendTransaction(db = database) {
      AccessGrantsTable.update({
        (AccessGrantsTable.tenantId eq tenantId.toKotlinUuid()) and
          AccessGrantsTable.validTo.isNull()
      }) {
        it[AccessGrantsTable.validTo] = expiredAt
      }
    }
}
