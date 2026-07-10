package ink.doa.workbench.worker.sprint

import ink.doa.workbench.core.messaging.EventMetadata
import ink.doa.workbench.core.port.messaging.DomainEventPublisher
import ink.doa.workbench.core.sprint.SprintCloseOperationRepository
import ink.doa.workbench.core.sprint.SprintRepository
import ink.doa.workbench.core.sprint.events.SprintCloseFailedEvent
import ink.doa.workbench.core.sprint.events.SprintCloseRequestedEvent
import ink.doa.workbench.core.sprint.events.SprintClosedEvent
import ink.doa.workbench.core.sprint.events.SprintDomainEvents
import ink.doa.workbench.core.sprint.model.SprintCloseOperationStatus
import ink.doa.workbench.core.workitem.ReassignSprintBatchCommand
import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.events.WorkItemSprintChangedEvent
import ink.doa.workbench.core.workitem.events.WorkItemSprintDomainEvents
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class SprintCloseRequestedEventHandler(
  private val operations: SprintCloseOperationRepository,
  private val sprints: SprintRepository,
  private val workItems: WorkItemRepository,
  private val events: DomainEventPublisher,
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
          result.changedItems.forEach { item ->
            events.publish(
              spec = WorkItemSprintDomainEvents.SprintChanged,
              key = item.apiId.value,
              payload =
                WorkItemSprintChangedEvent(
                  tenantId = tenantId.toString(),
                  projectId = projectId.toString(),
                  workItemId = item.apiId.value,
                  sourceSprintId = payload.sprintId,
                  targetSprintId = operation.targetSprintApiId?.value,
                  disposition = operation.disposition.name,
                  operationId = operation.apiId.value,
                  actorUserId = operation.requestedBy.toString(),
                ),
              metadata = EventMetadata(tenantId = tenantId.toString()),
            )
          }
          processed += result.processedItems
          remaining = result.remainingItems
          operations.updateProgress(operation.id, processed, 0, now())
        } while (remaining > 0)
      }
      sprints.markClosedFromClosing(
        tenantId = tenantId,
        projectId = projectId,
        sprintApiId = payload.sprintId,
        closedAt = now(),
        actorUserId = operation.requestedBy,
      )
      operations.markCompleted(operation.id, SprintCloseOperationStatus.SUCCEEDED, now())
      events.publish(
        spec = SprintDomainEvents.Closed,
        key = payload.sprintId,
        payload =
          SprintClosedEvent(
            tenantId = tenantId.toString(),
            projectId = projectId.toString(),
            sprintId = payload.sprintId,
            operationId = operation.apiId.value,
          ),
        metadata = EventMetadata(tenantId = tenantId.toString()),
      )
    } catch (error: Exception) {
      val message = error.message ?: error.javaClass.simpleName
      operations.markCompleted(
        operation.id,
        SprintCloseOperationStatus.FAILED,
        now(),
        message,
      )
      events.publish(
        spec = SprintDomainEvents.CloseFailed,
        key = payload.sprintId,
        payload =
          SprintCloseFailedEvent(
            tenantId = tenantId.toString(),
            projectId = projectId.toString(),
            sprintId = payload.sprintId,
            operationId = operation.apiId.value,
            error = message,
          ),
        metadata = EventMetadata(tenantId = tenantId.toString()),
      )
      logger.error("sprint_close_failed operationId={}", operation.apiId.value, error)
    }
  }

  private fun now(): OffsetDateTime = OffsetDateTime.now(clock.withZone(ZoneOffset.UTC))

  private fun parseUuid(value: String): UUID? = runCatching { UUID.fromString(value) }.getOrNull()
}
