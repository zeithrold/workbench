package one.ztd.workbench.data.repository.sprint

import java.time.OffsetDateTime
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import one.ztd.workbench.agile.sprint.SprintRepository
import one.ztd.workbench.agile.sprint.model.ArchiveSprintCommand
import one.ztd.workbench.agile.sprint.model.CreateSprintCommand
import one.ztd.workbench.agile.sprint.model.DeleteSprintCommand
import one.ztd.workbench.agile.sprint.model.SprintRecord
import one.ztd.workbench.agile.sprint.model.SprintStatus
import one.ztd.workbench.agile.sprint.model.UpdateSprintCommand
import one.ztd.workbench.data.persistence.postgres.workitem.SprintsTable
import one.ztd.workbench.data.persistence.postgres.workitem.now
import one.ztd.workbench.kernel.common.ids.PublicId
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

@Repository
class ExposedSprintRepository(private val database: Database) : SprintRepository {
  override suspend fun list(
    tenantId: UUID,
    projectId: UUID,
    status: SprintStatus?,
  ): List<SprintRecord> =
    suspendTransaction(db = database) {
      SprintsTable.selectAll()
        .where { activeSprintScope(tenantId, projectId, status) }
        .orderBy(SprintsTable.updatedAt to SortOrder.DESC)
        .map { it.toSprintRecord() }
    }

  override suspend fun findByApiId(
    tenantId: UUID,
    projectId: UUID,
    sprintApiId: String,
  ): SprintRecord? =
    suspendTransaction(db = database) {
      SprintsTable.selectAll()
        .where {
          activeSprintScope(tenantId, projectId) and (SprintsTable.apiId eq sprintApiId)
        }
        .singleOrNull()
        ?.toSprintRecord()
    }

  override suspend fun create(command: CreateSprintCommand): SprintRecord =
    suspendTransaction(db = database) {
      val sprintId = UUID.randomUUID()
      val apiId = PublicId.new("spr")
      val timestamp = now()
      SprintsTable.insert {
        it[SprintsTable.id] = sprintId.toKotlinUuid()
        it[SprintsTable.apiId] = apiId.value
        it[SprintsTable.tenantId] = command.tenantId.toKotlinUuid()
        it[SprintsTable.projectId] = command.projectId.toKotlinUuid()
        it[SprintsTable.name] = command.name
        it[SprintsTable.goal] = command.goal
        it[SprintsTable.status] = SprintStatus.PLANNED.dbValue
        it[SprintsTable.startAt] = command.startAt
        it[SprintsTable.endAt] = command.endAt
        it[SprintsTable.createdBy] = command.createdBy.toKotlinUuid()
        it[SprintsTable.createdAt] = timestamp
        it[SprintsTable.updatedAt] = timestamp
      }
      SprintRecord(
        id = sprintId,
        apiId = apiId,
        tenantId = command.tenantId,
        projectId = command.projectId,
        name = command.name,
        goal = command.goal,
        status = SprintStatus.PLANNED,
        startAt = command.startAt,
        endAt = command.endAt,
        closedAt = null,
        createdBy = command.createdBy,
        archivedAt = null,
        archivedBy = null,
        deletedAt = null,
        createdAt = timestamp,
        updatedAt = timestamp,
      )
    }

  override suspend fun update(command: UpdateSprintCommand): SprintRecord =
    suspendTransaction(db = database) {
      val row = requireSprintRow(command.tenantId, command.projectId, command.sprintApiId)
      val timestamp = now()
      SprintsTable.update({ SprintsTable.id eq row[SprintsTable.id] }) {
        command.name?.let { value -> it[SprintsTable.name] = value }
        command.goal?.let { value -> it[SprintsTable.goal] = value }
        command.startAt?.let { value -> it[SprintsTable.startAt] = value }
        command.endAt?.let { value -> it[SprintsTable.endAt] = value }
        it[SprintsTable.updatedAt] = timestamp
      }
      requireSprintRow(command.tenantId, command.projectId, command.sprintApiId).toSprintRecord()
    }

  override suspend fun markActive(
    tenantId: UUID,
    projectId: UUID,
    sprintApiId: String,
    startAt: OffsetDateTime,
    actorUserId: UUID,
  ): SprintRecord =
    suspendTransaction(db = database) {
      val row = requireSprintRow(tenantId, projectId, sprintApiId)
      val timestamp = now()
      SprintsTable.update({ SprintsTable.id eq row[SprintsTable.id] }) {
        it[SprintsTable.status] = SprintStatus.ACTIVE.dbValue
        it[SprintsTable.startAt] = startAt
        it[SprintsTable.updatedAt] = timestamp
      }
      requireSprintRow(tenantId, projectId, sprintApiId).toSprintRecord()
    }

  override suspend fun markClosed(
    tenantId: UUID,
    projectId: UUID,
    sprintApiId: String,
    closedAt: OffsetDateTime,
    actorUserId: UUID,
  ): SprintRecord =
    suspendTransaction(db = database) {
      val row = requireSprintRow(tenantId, projectId, sprintApiId)
      val timestamp = now()
      SprintsTable.update({ SprintsTable.id eq row[SprintsTable.id] }) {
        it[SprintsTable.status] = SprintStatus.CLOSED.dbValue
        it[SprintsTable.closedAt] = closedAt
        it[SprintsTable.updatedAt] = timestamp
      }
      requireSprintRow(tenantId, projectId, sprintApiId).toSprintRecord()
    }

  override suspend fun markClosing(
    tenantId: UUID,
    projectId: UUID,
    sprintApiId: String,
    actorUserId: UUID,
  ): SprintRecord =
    suspendTransaction(db = database) {
      val row = requireSprintRow(tenantId, projectId, sprintApiId)
      val timestamp = now()
      SprintsTable.update({ SprintsTable.id eq row[SprintsTable.id] }) {
        it[SprintsTable.status] = SprintStatus.CLOSING.dbValue
        it[SprintsTable.updatedAt] = timestamp
      }
      requireSprintRow(tenantId, projectId, sprintApiId).toSprintRecord()
    }

  override suspend fun markClosedFromClosing(
    tenantId: UUID,
    projectId: UUID,
    sprintApiId: String,
    closedAt: OffsetDateTime,
    actorUserId: UUID,
  ): SprintRecord =
    suspendTransaction(db = database) {
      val row = requireSprintRow(tenantId, projectId, sprintApiId)
      val timestamp = now()
      SprintsTable.update({
        (SprintsTable.id eq row[SprintsTable.id]) and
          (SprintsTable.status eq SprintStatus.CLOSING.dbValue)
      }) {
        it[SprintsTable.status] = SprintStatus.CLOSED.dbValue
        it[SprintsTable.closedAt] = closedAt
        it[SprintsTable.updatedAt] = timestamp
      }
      requireSprintRow(tenantId, projectId, sprintApiId).toSprintRecord()
    }

  override suspend fun markArchived(command: ArchiveSprintCommand): SprintRecord =
    suspendTransaction(db = database) {
      val row = requireSprintRow(command.tenantId, command.projectId, command.sprintApiId)
      val timestamp = now()
      SprintsTable.update({ SprintsTable.id eq row[SprintsTable.id] }) {
        it[SprintsTable.archivedAt] = timestamp
        it[SprintsTable.archivedBy] = command.actorUserId.toKotlinUuid()
        it[SprintsTable.updatedAt] = timestamp
      }
      row
        .toSprintRecord()
        .copy(
          archivedAt = timestamp,
          archivedBy = command.actorUserId,
          updatedAt = timestamp,
        )
    }

  override suspend fun softDelete(command: DeleteSprintCommand): Boolean =
    suspendTransaction(db = database) {
      val row = requireSprintRow(command.tenantId, command.projectId, command.sprintApiId)
      val timestamp = now()
      SprintsTable.update({ SprintsTable.id eq row[SprintsTable.id] }) {
        it[SprintsTable.deletedAt] = timestamp
        it[SprintsTable.deletedBy] = command.actorUserId.toKotlinUuid()
        it[SprintsTable.deleteReason] = command.deleteReason
        it[SprintsTable.updatedAt] = timestamp
      } > 0
    }

  override suspend fun countActiveByProject(
    tenantId: UUID,
    projectId: UUID,
    excludingSprintId: UUID?,
  ): Long =
    suspendTransaction(db = database) {
      var condition =
        activeSprintScope(tenantId, projectId) and
          (SprintsTable.status eq SprintStatus.ACTIVE.dbValue)
      if (excludingSprintId != null) {
        condition = condition and (SprintsTable.id neq excludingSprintId.toKotlinUuid())
      }
      SprintsTable.selectAll().where { condition }.count()
    }
}
