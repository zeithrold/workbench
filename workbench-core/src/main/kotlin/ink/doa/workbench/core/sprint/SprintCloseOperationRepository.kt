package ink.doa.workbench.core.sprint

import ink.doa.workbench.core.sprint.model.SprintCloseDisposition
import ink.doa.workbench.core.sprint.model.SprintCloseOperationRecord
import ink.doa.workbench.core.sprint.model.SprintCloseOperationStatus
import java.time.OffsetDateTime
import java.util.UUID

@Suppress("TooManyFunctions")
interface SprintCloseOperationRepository {
  @Suppress("LongParameterList")
  suspend fun createAndMarkClosing(
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
  ): SprintCloseOperationRecord

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
}
