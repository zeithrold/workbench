package one.ztd.workbench.agile.sprint

import java.time.OffsetDateTime
import java.util.UUID
import one.ztd.workbench.agile.sprint.model.SprintCloseDisposition
import one.ztd.workbench.agile.sprint.model.SprintCloseOperationRecord
import one.ztd.workbench.agile.sprint.model.SprintCloseOperationStatus

data class CreateSprintCloseOperationCommand(
  val tenantId: UUID,
  val projectId: UUID,
  val sprintId: UUID,
  val sprintApiId: String,
  val targetSprintId: UUID?,
  val targetSprintApiId: String?,
  val disposition: SprintCloseDisposition,
  val requestedBy: UUID,
  val idempotencyKey: String?,
  val createdAt: OffsetDateTime,
)

interface SprintCloseOperationRepository : SprintCloseOperationWriter, SprintCloseOperationReader

interface SprintCloseOperationWriter {
  suspend fun createAndMarkClosing(
    command: CreateSprintCloseOperationCommand
  ): SprintCloseOperationRecord

  suspend fun markRunning(id: UUID, startedAt: OffsetDateTime): Boolean

  suspend fun markQueued(id: UUID, updatedAt: OffsetDateTime): Boolean

  suspend fun retryAndEnqueue(request: SprintCloseRetryRequest): SprintCloseOperationRecord

  suspend fun updateProgress(
    id: UUID,
    processedItems: Int,
    failedItems: Int,
    updatedAt: OffsetDateTime,
  ): Boolean

  suspend fun setTotalItems(id: UUID, totalItems: Int): Boolean

  suspend fun markCompleted(
    id: UUID,
    status: SprintCloseOperationStatus,
    completedAt: OffsetDateTime,
    lastError: String? = null,
  ): Boolean

  suspend fun completeSucceeded(request: SprintCloseSuccessRequest): SprintCloseOperationRecord

  suspend fun completeFailed(request: SprintCloseFailureRequest): SprintCloseOperationRecord
}

interface SprintCloseOperationReader {
  suspend fun findByApiId(
    tenantId: UUID,
    projectId: UUID,
    sprintApiId: String,
    operationApiId: String,
  ): SprintCloseOperationRecord?

  suspend fun findByIdempotencyKey(
    tenantId: UUID,
    projectId: UUID,
    sprintId: UUID,
    idempotencyKey: String,
  ): SprintCloseOperationRecord?
}
