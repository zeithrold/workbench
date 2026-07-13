package ink.doa.workbench.application.jobs.sprint

import ink.doa.workbench.agile.sprint.SprintCloseFailureRequest
import ink.doa.workbench.agile.sprint.SprintCloseOperationRepository
import ink.doa.workbench.agile.sprint.SprintCloseSuccessRequest
import ink.doa.workbench.agile.sprint.events.SprintCloseRequestedEvent
import ink.doa.workbench.agile.sprint.model.SprintCloseDisposition
import ink.doa.workbench.agile.sprint.model.SprintCloseOperationRecord
import ink.doa.workbench.agile.sprint.model.SprintCloseOperationStatus
import ink.doa.workbench.agile.workitem.ReassignSprintBatchCommand
import ink.doa.workbench.agile.workitem.ReassignSprintBatchResult
import ink.doa.workbench.agile.workitem.WorkItemRepository
import ink.doa.workbench.agile.workitem.model.WorkItemRecord
import ink.doa.workbench.agile.workitem.model.WorkItemStatusGroup
import ink.doa.workbench.kernel.common.ids.PublicId
import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.JsonObject

class SprintCloseRequestedEventHandlerTest :
  FunSpec({
    val operations = mockk<SprintCloseOperationRepository>(relaxed = true)
    val workItems = mockk<WorkItemRepository>(relaxed = true)
    val clock = Clock.fixed(Instant.parse("2026-07-10T00:00:00Z"), ZoneOffset.UTC)
    val handler = SprintCloseRequestedEventHandler(operations, workItems, clock)
    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()
    val sprintId = UUID.randomUUID()
    val actorId = UUID.randomUUID()
    val now = OffsetDateTime.parse("2026-07-10T00:00:00Z")
    val sourceSprintApiId = "spr_01JABCDEFGHJKMNPQRSTVWXYZ0"
    val operation =
      SprintCloseOperationRecord(
        id = UUID.randomUUID(),
        apiId = PublicId("sop_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        tenantId = tenantId,
        projectId = projectId,
        sprintId = sprintId,
        sprintApiId = PublicId(sourceSprintApiId),
        targetSprintId = null,
        targetSprintApiId = null,
        disposition = SprintCloseDisposition.BACKLOG,
        requestedBy = actorId,
        status = SprintCloseOperationStatus.QUEUED,
        totalItems = 1,
        processedItems = 0,
        failedItems = 0,
        lastError = null,
        idempotencyKey = "close-1",
        createdAt = now,
        startedAt = null,
        completedAt = null,
      )
    val workItem =
      WorkItemRecord(
        id = UUID.randomUUID(),
        apiId = PublicId("wki_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        tenantId = tenantId,
        projectId = projectId,
        issueTypeApiId = PublicId("wit_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        issueTypeConfigApiId = PublicId("wic_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        key = "CORE-1",
        title = "Work",
        description = null,
        statusId = UUID.randomUUID(),
        statusApiId = PublicId("sts_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        statusGroup = WorkItemStatusGroup.IN_PROGRESS,
        reporterId = actorId,
        assigneeId = null,
        priorityApiId = null,
        reporterApiId = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        assigneeApiId = null,
        sprintApiId = null,
        properties = JsonObject(emptyMap()),
        createdAt = now,
        updatedAt = now,
      )

    test("backlog disposition batches changes then completes operation in repository") {
      coEvery {
        operations.findByApiId(tenantId, projectId, sourceSprintApiId, operation.apiId.value)
      } returns operation
      coEvery { operations.markRunning(operation.id, any()) } returns true
      coEvery { workItems.countUnfinishedBySprint(tenantId, projectId, sprintId) } returns 1
      coEvery { workItems.reassignSprintBatch(any()) } returns
        ReassignSprintBatchResult(1, 0, listOf(workItem))
      coEvery { operations.completeSucceeded(any()) } returns
        operation.copy(status = SprintCloseOperationStatus.SUCCEEDED)

      handler.handle(
        SprintCloseRequestedEvent(
          tenantId = tenantId.toString(),
          projectId = projectId.toString(),
          sprintId = sourceSprintApiId,
          operationId = operation.apiId.value,
          requestedBy = actorId.toString(),
        )
      )

      coVerify {
        workItems.reassignSprintBatch(
          ReassignSprintBatchCommand(
            tenantId = tenantId,
            projectId = projectId,
            sourceSprintId = sprintId,
            targetSprintId = null,
            disposition = SprintCloseDisposition.BACKLOG,
            actorUserId = actorId,
            operationId = operation.apiId.value,
            limit = 100,
          )
        )
      }
      coVerify {
        operations.completeSucceeded(
          SprintCloseSuccessRequest(
            operationId = operation.id,
            tenantId = tenantId,
            projectId = projectId,
            sprintApiId = sourceSprintApiId,
            operationApiId = operation.apiId.value,
            closedAt = now,
            actorUserId = actorId,
          )
        )
      }
    }

    test("failure records completion through repository") {
      coEvery {
        operations.findByApiId(tenantId, projectId, sourceSprintApiId, operation.apiId.value)
      } returns operation
      coEvery { operations.markRunning(operation.id, any()) } returns true
      coEvery { workItems.countUnfinishedBySprint(tenantId, projectId, sprintId) } returns 1
      coEvery { workItems.reassignSprintBatch(any()) } throws IllegalStateException("batch failed")
      coEvery { operations.completeFailed(any()) } returns
        operation.copy(status = SprintCloseOperationStatus.FAILED, lastError = "batch failed")

      handler.handle(
        SprintCloseRequestedEvent(
          tenantId = tenantId.toString(),
          projectId = projectId.toString(),
          sprintId = sourceSprintApiId,
          operationId = operation.apiId.value,
          requestedBy = actorId.toString(),
        )
      )

      coVerify {
        operations.completeFailed(
          SprintCloseFailureRequest(
            operationId = operation.id,
            tenantId = tenantId,
            projectId = projectId,
            sprintApiId = sourceSprintApiId,
            operationApiId = operation.apiId.value,
            error = "batch failed",
            completedAt = now,
          )
        )
      }
    }
  })
