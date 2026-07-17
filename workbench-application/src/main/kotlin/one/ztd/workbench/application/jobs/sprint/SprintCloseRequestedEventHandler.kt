package one.ztd.workbench.application.jobs.sprint

import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import one.ztd.workbench.agile.sprint.SprintCloseFailureRequest
import one.ztd.workbench.agile.sprint.SprintCloseOperationRepository
import one.ztd.workbench.agile.sprint.SprintCloseSuccessRequest
import one.ztd.workbench.agile.sprint.events.SprintCloseRequestedEvent
import one.ztd.workbench.agile.sprint.model.SprintCloseOperationRecord
import one.ztd.workbench.agile.sprint.model.SprintCloseOperationStatus
import one.ztd.workbench.agile.workitem.ReassignSprintBatchCommand
import one.ztd.workbench.agile.workitem.WorkItemRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@one.ztd.workbench.application.jobs.JobsEnabled
@Component
class SprintCloseRequestedEventHandler(
  private val operations: SprintCloseOperationRepository,
  private val workItems: WorkItemRepository,
  private val clock: Clock,
) {
  private val logger = LoggerFactory.getLogger(javaClass)

  suspend fun handle(payload: SprintCloseRequestedEvent) {
    val tenantId = parseUuid(payload.tenantId) ?: return
    val projectId = parseUuid(payload.projectId) ?: return
    val operation =
      operations.findByApiId(tenantId, projectId, payload.sprintId, payload.operationId) ?: return
    if (operation.status == SprintCloseOperationStatus.SUCCEEDED) return
    operations.markRunning(operation.id, now())
    operations.setTotalItems(
      operation.id,
      workItems.countUnfinishedBySprint(tenantId, projectId, operation.sprintId).toInt(),
    )
    try {
      reassignWorkItems(tenantId, projectId, operation)
      completeSucceeded(payload, tenantId, projectId, operation)
    } catch (
      // Consumer boundary: every processing failure must complete the persisted operation.
      @Suppress("TooGenericExceptionCaught") error: Exception) {
      completeFailed(payload, tenantId, projectId, operation, error)
    }
  }

  private suspend fun reassignWorkItems(
    tenantId: UUID,
    projectId: UUID,
    operation: SprintCloseOperationRecord,
  ) {
    if (operation.disposition == one.ztd.workbench.agile.sprint.model.SprintCloseDisposition.KEEP)
      return
    var processed = operation.processedItems
    do {
      val result = workItems.reassignSprintBatch(reassignCommand(tenantId, projectId, operation))
      processed += result.processedItems
      operations.updateProgress(operation.id, processed, 0, now())
    } while (result.remainingItems > 0)
  }

  private fun reassignCommand(
    tenantId: UUID,
    projectId: UUID,
    operation: SprintCloseOperationRecord,
  ) =
    ReassignSprintBatchCommand(
      tenantId = tenantId,
      projectId = projectId,
      sourceSprintId = operation.sprintId,
      targetSprintId = operation.targetSprintId,
      disposition = operation.disposition,
      actorUserId = operation.requestedBy,
      operationId = operation.apiId.value,
    )

  private suspend fun completeSucceeded(
    payload: SprintCloseRequestedEvent,
    tenantId: UUID,
    projectId: UUID,
    operation: SprintCloseOperationRecord,
  ) {
    operations.completeSucceeded(
      SprintCloseSuccessRequest(
        operationId = operation.id,
        tenantId = tenantId,
        projectId = projectId,
        sprintApiId = payload.sprintId,
        operationApiId = operation.apiId.value,
        closedAt = now(),
        actorUserId = operation.requestedBy,
      )
    )
  }

  private suspend fun completeFailed(
    payload: SprintCloseRequestedEvent,
    tenantId: UUID,
    projectId: UUID,
    operation: SprintCloseOperationRecord,
    error: Exception,
  ) {
    operations.completeFailed(
      SprintCloseFailureRequest(
        operationId = operation.id,
        tenantId = tenantId,
        projectId = projectId,
        sprintApiId = payload.sprintId,
        operationApiId = operation.apiId.value,
        error = error.message ?: error.javaClass.simpleName,
        completedAt = now(),
      )
    )
    logger.error("sprint_close_failed operationId={}", operation.apiId.value, error)
  }

  private fun now(): OffsetDateTime = OffsetDateTime.now(clock.withZone(ZoneOffset.UTC))

  private fun parseUuid(value: String): UUID? = runCatching { UUID.fromString(value) }.getOrNull()
}
