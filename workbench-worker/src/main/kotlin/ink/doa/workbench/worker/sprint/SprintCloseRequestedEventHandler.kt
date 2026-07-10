package ink.doa.workbench.worker.sprint

import ink.doa.workbench.core.sprint.SprintCloseFailureRequest
import ink.doa.workbench.core.sprint.SprintCloseOperationRepository
import ink.doa.workbench.core.sprint.SprintCloseSuccessRequest
import ink.doa.workbench.core.sprint.events.SprintCloseRequestedEvent
import ink.doa.workbench.core.sprint.model.SprintCloseOperationStatus
import ink.doa.workbench.core.workitem.ReassignSprintBatchCommand
import ink.doa.workbench.core.workitem.WorkItemRepository
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class SprintCloseRequestedEventHandler(
  private val operations: SprintCloseOperationRepository,
  private val workItems: WorkItemRepository,
  private val clock: Clock,
) {
  private val logger = LoggerFactory.getLogger(javaClass)

  @Suppress("LongMethod", "TooGenericExceptionCaught")
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
      var processed = operation.processedItems
      if (
        operation.disposition != ink.doa.workbench.core.sprint.model.SprintCloseDisposition.KEEP
      ) {
        var remaining: Int
        do {
          val result =
            workItems.reassignSprintBatch(
              ReassignSprintBatchCommand(
                tenantId = tenantId,
                projectId = projectId,
                sourceSprintId = operation.sprintId,
                targetSprintId = operation.targetSprintId,
                disposition = operation.disposition,
                actorUserId = operation.requestedBy,
                operationId = operation.apiId.value,
              )
            )
          processed += result.processedItems
          remaining = result.remainingItems
          operations.updateProgress(operation.id, processed, 0, now())
        } while (remaining > 0)
      }
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
    } catch (error: Exception) {
      val message = error.message ?: error.javaClass.simpleName
      operations.completeFailed(
        SprintCloseFailureRequest(
          operationId = operation.id,
          tenantId = tenantId,
          projectId = projectId,
          sprintApiId = payload.sprintId,
          operationApiId = operation.apiId.value,
          error = message,
          completedAt = now(),
        )
      )
      logger.error("sprint_close_failed operationId={}", operation.apiId.value, error)
    }
  }

  private fun now(): OffsetDateTime = OffsetDateTime.now(clock.withZone(ZoneOffset.UTC))

  private fun parseUuid(value: String): UUID? = runCatching { UUID.fromString(value) }.getOrNull()
}
