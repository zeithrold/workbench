package ink.doa.workbench.data.permission

import ink.doa.workbench.core.permission.CreatePermissionActionCommand
import ink.doa.workbench.core.permission.PermissionActionRecord
import ink.doa.workbench.core.permission.PermissionActionRepository
import ink.doa.workbench.core.permission.model.AuthorizationAction
import ink.doa.workbench.data.persistence.PermissionActionsTable
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.springframework.stereotype.Repository

@Repository
class ExposedPermissionActionRepository(private val database: Database) :
  PermissionActionRepository {
  override suspend fun upsert(command: CreatePermissionActionCommand): PermissionActionRecord =
    suspendTransaction(db = database) {
      val existing =
        PermissionActionsTable.selectAll()
          .where { PermissionActionsTable.code eq command.code.code }
          .singleOrNull()
      if (existing != null) {
        existing.toPermissionActionRecord()
      } else {
        val id = UUID.randomUUID()
        val now = AdminRepositoryMappers.nowUtc()
        PermissionActionsTable.insert {
          it[PermissionActionsTable.id] = id.toKotlinUuid()
          it[code] = command.code.code
          it[description] = command.description
          it[createdAt] = now
        }
        PermissionActionsTable.selectAll()
          .where { PermissionActionsTable.id eq id.toKotlinUuid() }
          .single()
          .toPermissionActionRecord()
      }
    }

  override suspend fun findByCode(code: AuthorizationAction): PermissionActionRecord? =
    suspendTransaction(db = database) {
      PermissionActionsTable.selectAll()
        .where { PermissionActionsTable.code eq code.code }
        .singleOrNull()
        ?.toPermissionActionRecord()
    }

  override suspend fun list(): List<PermissionActionRecord> =
    suspendTransaction(db = database) {
      PermissionActionsTable.selectAll().orderBy(PermissionActionsTable.code to SortOrder.ASC).map {
        it.toPermissionActionRecord()
      }
    }
}

private fun ResultRow.toPermissionActionRecord() =
  PermissionActionRecord(
    id = this[PermissionActionsTable.id].toJavaUuid(),
    code = AuthorizationAction(this[PermissionActionsTable.code]),
    description = this[PermissionActionsTable.description],
    createdAt = this[PermissionActionsTable.createdAt],
  )
