package ink.doa.workbench.data.repository.sprint

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.messaging.EventMetadata
import ink.doa.workbench.core.port.messaging.DomainEventOutbox
import ink.doa.workbench.core.sprint.SprintCloseOperationRepository
import ink.doa.workbench.core.sprint.events.SprintDomainEvents
import ink.doa.workbench.core.sprint.model.SprintCloseDisposition
import ink.doa.workbench.core.sprint.model.SprintCloseOperationRecord
import ink.doa.workbench.core.sprint.model.SprintCloseOperationStatus
import ink.doa.workbench.data.persistence.postgres.workitem.SprintCloseOperationsTable
import ink.doa.workbench.data.persistence.postgres.workitem.SprintsTable
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

@Repository
class ExposedSprintCloseOperationRepository(
  private val database: org.jetbrains.exposed.v1.jdbc.Database,
  private val outbox: DomainEventOutbox,
) : SprintCloseOperationRepository {
  override suspend fun createAndMarkClosing(
    tenantId: UUID,
    projectId: UUID,
    sprintId: UUID,
    sprintApiId: String,
    targetSprintId: UUID?,
    targetSprintApiId: String?,
    disposition: SprintCloseDisposition,
    requestedBy: UUID,
    idempotencyKey: String?,
    createdAt: OffsetDateTime,
  ): SprintCloseOperationRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val apiId = PublicId.new("sop")
      SprintCloseOperationsTable.insert {
        it[SprintCloseOperationsTable.id] = id.toKotlinUuid()
        it[SprintCloseOperationsTable.apiId] = apiId.value
        it[SprintCloseOperationsTable.tenantId] = tenantId.toKotlinUuid()
        it[SprintCloseOperationsTable.projectId] = projectId.toKotlinUuid()
        it[SprintCloseOperationsTable.sprintId] = sprintId.toKotlinUuid()
        it[SprintCloseOperationsTable.targetSprintId] = targetSprintId?.toKotlinUuid()
        it[SprintCloseOperationsTable.disposition] = disposition.name
        it[SprintCloseOperationsTable.requestedBy] = requestedBy.toKotlinUuid()
        it[SprintCloseOperationsTable.status] = SprintCloseOperationStatus.QUEUED.name
        it[SprintCloseOperationsTable.idempotencyKey] = idempotencyKey
        it[SprintCloseOperationsTable.createdAt] = createdAt
        it[SprintCloseOperationsTable.totalItems] = 0
        it[SprintCloseOperationsTable.processedItems] = 0
        it[SprintCloseOperationsTable.failedItems] = 0
      }
      val markedClosing =
        SprintsTable.update({
          (SprintsTable.id eq sprintId.toKotlinUuid()) and (SprintsTable.status eq "active")
        }) {
          it[SprintsTable.status] = "closing"
          it[SprintsTable.updatedAt] = createdAt
        }
      require(markedClosing == 1) { "Sprint is no longer active." }
      outbox.append(
        spec = SprintDomainEvents.CloseRequested,
        key = sprintApiId,
        payload =
          ink.doa.workbench.core.sprint.events.SprintCloseRequestedEvent(
            tenantId = tenantId.toString(),
            projectId = projectId.toString(),
            sprintId = sprintApiId,
            operationId = apiId.value,
            requestedBy = requestedBy.toString(),
          ),
        metadata = EventMetadata(tenantId = tenantId.toString()),
      )
      toRecord(
        SprintCloseOperationsTable.selectAll()
          .where { SprintCloseOperationsTable.id eq id.toKotlinUuid() }
          .single()
      )
    }

  override suspend fun findByApiId(
    tenantId: UUID,
    projectId: UUID,
    sprintApiId: String,
    operationApiId: String,
  ): SprintCloseOperationRecord? =
    suspendTransaction(db = database) {
      SprintCloseOperationsTable.join(
          SprintsTable,
          org.jetbrains.exposed.v1.core.JoinType.INNER,
          SprintCloseOperationsTable.sprintId,
          SprintsTable.id,
        )
        .selectAll()
        .where {
          (SprintCloseOperationsTable.tenantId eq tenantId.toKotlinUuid()) and
            (SprintCloseOperationsTable.projectId eq projectId.toKotlinUuid()) and
            (SprintsTable.apiId eq sprintApiId) and
            (SprintCloseOperationsTable.apiId eq operationApiId)
        }
        .singleOrNull()
        ?.let(::toRecord)
    }

  override suspend fun findByIdempotencyKey(
    tenantId: UUID,
    projectId: UUID,
    sprintId: UUID,
    idempotencyKey: String,
  ): SprintCloseOperationRecord? =
    suspendTransaction(db = database) {
      SprintCloseOperationsTable.selectAll()
        .where {
          (SprintCloseOperationsTable.tenantId eq tenantId.toKotlinUuid()) and
            (SprintCloseOperationsTable.projectId eq projectId.toKotlinUuid()) and
            (SprintCloseOperationsTable.sprintId eq sprintId.toKotlinUuid()) and
            (SprintCloseOperationsTable.idempotencyKey eq idempotencyKey)
        }
        .singleOrNull()
        ?.let(::toRecord)
    }

  override suspend fun markRunning(id: UUID, startedAt: OffsetDateTime): Boolean =
    suspendTransaction(db = database) {
      SprintCloseOperationsTable.update({
        (SprintCloseOperationsTable.id eq id.toKotlinUuid()) and
          (SprintCloseOperationsTable.status eq SprintCloseOperationStatus.QUEUED.name)
      }) {
        it[SprintCloseOperationsTable.status] = SprintCloseOperationStatus.RUNNING.name
        it[SprintCloseOperationsTable.startedAt] = startedAt
      } > 0
    }

  override suspend fun markQueued(id: UUID, updatedAt: OffsetDateTime): Boolean =
    suspendTransaction(db = database) {
      SprintCloseOperationsTable.update({
        (SprintCloseOperationsTable.id eq id.toKotlinUuid()) and
          (SprintCloseOperationsTable.status eq SprintCloseOperationStatus.FAILED.name)
      }) {
        it[SprintCloseOperationsTable.status] = SprintCloseOperationStatus.QUEUED.name
        it[SprintCloseOperationsTable.lastError] = null
        it[SprintCloseOperationsTable.completedAt] = null
      } > 0
    }

  override suspend fun updateProgress(
    id: UUID,
    processedItems: Int,
    failedItems: Int,
    updatedAt: OffsetDateTime,
  ): Boolean =
    suspendTransaction(db = database) {
      SprintCloseOperationsTable.update({ SprintCloseOperationsTable.id eq id.toKotlinUuid() }) {
        it[SprintCloseOperationsTable.processedItems] = processedItems
        it[SprintCloseOperationsTable.failedItems] = failedItems
      } > 0
    }

  override suspend fun setTotalItems(id: UUID, totalItems: Int): Boolean =
    suspendTransaction(db = database) {
      SprintCloseOperationsTable.update({ SprintCloseOperationsTable.id eq id.toKotlinUuid() }) {
        it[SprintCloseOperationsTable.totalItems] = totalItems
      } > 0
    }

  override suspend fun markCompleted(
    id: UUID,
    status: SprintCloseOperationStatus,
    completedAt: OffsetDateTime,
    lastError: String?,
  ): Boolean =
    suspendTransaction(db = database) {
      SprintCloseOperationsTable.update({ SprintCloseOperationsTable.id eq id.toKotlinUuid() }) {
        it[SprintCloseOperationsTable.status] = status.name
        it[SprintCloseOperationsTable.completedAt] = completedAt
        it[SprintCloseOperationsTable.lastError] = lastError
      } > 0
    }

  private fun toRecord(row: org.jetbrains.exposed.v1.core.ResultRow): SprintCloseOperationRecord {
    val sprintId = row[SprintCloseOperationsTable.sprintId].toJavaUuid()
    val targetId = row[SprintCloseOperationsTable.targetSprintId]?.toJavaUuid()
    return SprintCloseOperationRecord(
      id = row[SprintCloseOperationsTable.id].toJavaUuid(),
      apiId = PublicId(row[SprintCloseOperationsTable.apiId]),
      tenantId = row[SprintCloseOperationsTable.tenantId].toJavaUuid(),
      projectId = row[SprintCloseOperationsTable.projectId].toJavaUuid(),
      sprintId = sprintId,
      sprintApiId =
        PublicId(
          SprintsTable.selectAll()
            .where { SprintsTable.id eq sprintId.toKotlinUuid() }
            .single()[SprintsTable.apiId]
        ),
      targetSprintId = targetId,
      targetSprintApiId =
        targetId?.let { id ->
          SprintsTable.selectAll()
            .where { SprintsTable.id eq id.toKotlinUuid() }
            .singleOrNull()
            ?.get(SprintsTable.apiId)
            ?.let(::PublicId)
        },
      disposition = SprintCloseDisposition.valueOf(row[SprintCloseOperationsTable.disposition]),
      requestedBy = row[SprintCloseOperationsTable.requestedBy].toJavaUuid(),
      status = SprintCloseOperationStatus.valueOf(row[SprintCloseOperationsTable.status]),
      totalItems = row[SprintCloseOperationsTable.totalItems],
      processedItems = row[SprintCloseOperationsTable.processedItems],
      failedItems = row[SprintCloseOperationsTable.failedItems],
      lastError = row[SprintCloseOperationsTable.lastError],
      idempotencyKey = row[SprintCloseOperationsTable.idempotencyKey],
      createdAt = row[SprintCloseOperationsTable.createdAt],
      startedAt = row[SprintCloseOperationsTable.startedAt],
      completedAt = row[SprintCloseOperationsTable.completedAt],
    )
  }
}
