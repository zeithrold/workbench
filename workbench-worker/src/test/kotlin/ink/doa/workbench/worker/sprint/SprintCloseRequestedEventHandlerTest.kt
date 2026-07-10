package ink.doa.workbench.worker.sprint

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.port.messaging.DomainEventPublisher
import ink.doa.workbench.core.sprint.SprintCloseOperationRepository
import ink.doa.workbench.core.sprint.SprintRepository
import ink.doa.workbench.core.sprint.events.SprintCloseRequestedEvent
import ink.doa.workbench.core.sprint.model.SprintCloseDisposition
import ink.doa.workbench.core.sprint.model.SprintCloseOperationRecord
import ink.doa.workbench.core.sprint.model.SprintCloseOperationStatus
import ink.doa.workbench.core.sprint.model.SprintRecord
import ink.doa.workbench.core.sprint.model.SprintStatus
import ink.doa.workbench.core.workitem.ReassignSprintBatchCommand
import ink.doa.workbench.core.workitem.ReassignSprintBatchResult
import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
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
    val sprints = mockk<SprintRepository>(relaxed = true)
    val workItems = mockk<WorkItemRepository>(relaxed = true)
    val events = mockk<DomainEventPublisher>(relaxed = true)
    val clock = Clock.fixed(Instant.parse("2026-07-10T00:00:00Z"), ZoneOffset.UTC)
    val handler = SprintCloseRequestedEventHandler(operations, sprints, workItems, events, clock)
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
    val sprint =
      SprintRecord(
        id = sprintId,
        apiId = PublicId(sourceSprintApiId),
        tenantId = tenantId,
        projectId = projectId,
        name = "Sprint",
        goal = null,
        status = SprintStatus.CLOSING,
        startAt = now,
        endAt = null,
        closedAt = null,
        createdBy = actorId,
        archivedAt = null,
        archivedBy = null,
        deletedAt = null,
        createdAt = now,
        updatedAt = now,
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

    test("backlog disposition batches changes then closes sprint") {
      coEvery {
        operations.findByApiId(tenantId, projectId, sourceSprintApiId, operation.apiId.value)
      } returns operation
      coEvery { operations.markRunning(operation.id, any()) } returns true
      coEvery { workItems.countUnfinishedBySprint(tenantId, projectId, sprintId) } returns 1
      coEvery { workItems.reassignSprintBatch(any()) } returns
        ReassignSprintBatchResult(1, 0, listOf(workItem))
      coEvery {
        sprints.markClosedFromClosing(tenantId, projectId, sourceSprintApiId, any(), actorId)
      } returns sprint.copy(status = SprintStatus.CLOSED)

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
        operations.markCompleted(operation.id, SprintCloseOperationStatus.SUCCEEDED, any())
      }
    }
  })
