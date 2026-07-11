package ink.doa.workbench.data.repository.sprint

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.messaging.EventMetadata
import ink.doa.workbench.core.port.messaging.DomainEventOutbox
import ink.doa.workbench.core.sprint.CreateSprintCloseOperationCommand
import ink.doa.workbench.core.sprint.SprintCloseFailureRequest
import ink.doa.workbench.core.sprint.SprintCloseOperationRepository
import ink.doa.workbench.core.sprint.SprintCloseRetryRequest
import ink.doa.workbench.core.sprint.SprintCloseSuccessRequest
import ink.doa.workbench.core.sprint.events.SprintCloseFailedEvent
import ink.doa.workbench.core.sprint.events.SprintClosedEvent
import ink.doa.workbench.core.sprint.events.SprintDomainEvents
import ink.doa.workbench.core.sprint.model.SprintCloseDisposition
import ink.doa.workbench.core.sprint.model.SprintCloseOperationRecord
import ink.doa.workbench.core.sprint.model.SprintCloseOperationStatus
import ink.doa.workbench.core.sprint.model.SprintStatus
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
    command: CreateSprintCloseOperationCommand
  ): SprintCloseOperationRecord =
    suspendTransaction(db = database) {
      val tenantId = command.tenantId
      val projectId = command.projectId
      val sprintId = command.sprintId
      val sprintApiId = command.sprintApiId
      val requestedBy = command.requestedBy
      val createdAt = command.createdAt
      val id = UUID.randomUUID()
      val apiId = PublicId.new("sop")
      SprintCloseOperationsTable.insert {
        it[SprintCloseOperationsTable.id] = id.toKotlinUuid()
        it[SprintCloseOperationsTable.apiId] = apiId.value
        it[SprintCloseOperationsTable.tenantId] = tenantId.toKotlinUuid()
        it[SprintCloseOperationsTable.projectId] = projectId.toKotlinUuid()
        it[SprintCloseOperationsTable.sprintId] = sprintId.toKotlinUuid()
        it[SprintCloseOperationsTable.targetSprintId] = command.targetSprintId?.toKotlinUuid()
        it[SprintCloseOperationsTable.disposition] = command.disposition.name
        it[SprintCloseOperationsTable.requestedBy] = requestedBy.toKotlinUuid()
        it[SprintCloseOperationsTable.status] = SprintCloseOperationStatus.QUEUED.name
        it[SprintCloseOperationsTable.idempotencyKey] = command.idempotencyKey
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

  override suspend fun retryAndEnqueue(
    request: SprintCloseRetryRequest
  ): SprintCloseOperationRecord =
    suspendTransaction(db = database) {
      val operation =
        SprintCloseOperationsTable.join(
            SprintsTable,
            org.jetbrains.exposed.v1.core.JoinType.INNER,
            SprintCloseOperationsTable.sprintId,
            SprintsTable.id,
          )
          .selectAll()
          .where {
            (SprintCloseOperationsTable.tenantId eq request.tenantId.toKotlinUuid()) and
              (SprintCloseOperationsTable.projectId eq request.projectId.toKotlinUuid()) and
              (SprintsTable.apiId eq request.sprintApiId) and
              (SprintCloseOperationsTable.apiId eq request.operationApiId)
          }
          .singleOrNull()
          ?.let(::toRecord)
          ?: throw ink.doa.workbench.core.common.errors.ResourceNotFoundException(
            ink.doa.workbench.core.common.errors.WorkbenchErrorCode.SPRINT_CLOSE_OPERATION_NOT_FOUND
          )
      if (operation.status != SprintCloseOperationStatus.FAILED) {
        throw ink.doa.workbench.core.common.errors.InvalidRequestException(
          ink.doa.workbench.core.common.errors.WorkbenchErrorCode.SPRINT_CLOSE_OPERATION_CONFLICT
        )
      }
      SprintCloseOperationsTable.update({
        (SprintCloseOperationsTable.id eq operation.id.toKotlinUuid()) and
          (SprintCloseOperationsTable.status eq SprintCloseOperationStatus.FAILED.name)
      }) {
        it[SprintCloseOperationsTable.status] = SprintCloseOperationStatus.QUEUED.name
        it[SprintCloseOperationsTable.lastError] = null
        it[SprintCloseOperationsTable.completedAt] = null
      }
      outbox.append(
        spec = SprintDomainEvents.CloseRequested,
        key = request.sprintApiId,
        payload = request.payload,
        metadata = EventMetadata(tenantId = request.metadataTenantId),
      )
      toRecord(
        SprintCloseOperationsTable.selectAll()
          .where { SprintCloseOperationsTable.id eq operation.id.toKotlinUuid() }
          .single()
      )
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

  override suspend fun completeSucceeded(
    request: SprintCloseSuccessRequest
  ): SprintCloseOperationRecord =
    suspendTransaction(db = database) {
      val markedClosed =
        SprintsTable.update({
          (SprintsTable.tenantId eq request.tenantId.toKotlinUuid()) and
            (SprintsTable.projectId eq request.projectId.toKotlinUuid()) and
            (SprintsTable.apiId eq request.sprintApiId) and
            (SprintsTable.status eq SprintStatus.CLOSING.dbValue)
        }) {
          it[SprintsTable.status] = SprintStatus.CLOSED.dbValue
          it[SprintsTable.closedAt] = request.closedAt
          it[SprintsTable.updatedAt] = request.closedAt
        }
      require(markedClosed == 1) { "Sprint is no longer closing." }
      SprintCloseOperationsTable.update({
        SprintCloseOperationsTable.id eq request.operationId.toKotlinUuid()
      }) {
        it[SprintCloseOperationsTable.status] = SprintCloseOperationStatus.SUCCEEDED.name
        it[SprintCloseOperationsTable.completedAt] = request.closedAt
        it[SprintCloseOperationsTable.lastError] = null
      }
      outbox.append(
        spec = SprintDomainEvents.Closed,
        key = request.sprintApiId,
        payload =
          SprintClosedEvent(
            tenantId = request.tenantId.toString(),
            projectId = request.projectId.toString(),
            sprintId = request.sprintApiId,
            operationId = request.operationApiId,
          ),
        metadata = EventMetadata(tenantId = request.tenantId.toString()),
      )
      toRecord(
        SprintCloseOperationsTable.selectAll()
          .where { SprintCloseOperationsTable.id eq request.operationId.toKotlinUuid() }
          .single()
      )
    }

  override suspend fun completeFailed(
    request: SprintCloseFailureRequest
  ): SprintCloseOperationRecord =
    suspendTransaction(db = database) {
      SprintCloseOperationsTable.update({
        SprintCloseOperationsTable.id eq request.operationId.toKotlinUuid()
      }) {
        it[SprintCloseOperationsTable.status] = SprintCloseOperationStatus.FAILED.name
        it[SprintCloseOperationsTable.completedAt] = request.completedAt
        it[SprintCloseOperationsTable.lastError] = request.error
      }
      outbox.append(
        spec = SprintDomainEvents.CloseFailed,
        key = request.sprintApiId,
        payload =
          SprintCloseFailedEvent(
            tenantId = request.tenantId.toString(),
            projectId = request.projectId.toString(),
            sprintId = request.sprintApiId,
            operationId = request.operationApiId,
            error = request.error,
          ),
        metadata = EventMetadata(tenantId = request.tenantId.toString()),
      )
      toRecord(
        SprintCloseOperationsTable.selectAll()
          .where { SprintCloseOperationsTable.id eq request.operationId.toKotlinUuid() }
          .single()
      )
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
