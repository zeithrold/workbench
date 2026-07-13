package ink.doa.workbench.data.messaging

import ink.doa.workbench.agile.sprint.model.SprintCloseDisposition
import ink.doa.workbench.agile.workitem.ReassignSprintBatchCommand
import ink.doa.workbench.agile.workitem.events.WorkItemSprintDomainEvents
import ink.doa.workbench.agile.workitem.model.WorkItemRecord
import ink.doa.workbench.agile.workitem.model.WorkItemStatusGroup
import ink.doa.workbench.application.messaging.DomainEventOutbox
import ink.doa.workbench.kernel.common.ids.PublicId
import ink.doa.workbench.kernel.messaging.EventMetadata
import io.kotest.core.spec.style.StringSpec
import io.mockk.mockk
import io.mockk.verify
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.JsonObject

class WorkItemOutboxAppenderTest :
  StringSpec({
    val outbox = mockk<DomainEventOutbox>(relaxed = true)
    val appender = WorkItemOutboxAppender(outbox)
    val now = OffsetDateTime.now(ZoneOffset.UTC)

    "appendSprintChanged writes sprint changed event to outbox" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val workItem =
        WorkItemRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("wki"),
          tenantId = tenantId,
          projectId = projectId,
          issueTypeApiId = PublicId.new("typ"),
          issueTypeConfigApiId = PublicId.new("cfg"),
          key = "CORE-1",
          title = "Task",
          description = null,
          statusId = UUID.randomUUID(),
          statusApiId = PublicId.new("sts"),
          statusGroup = WorkItemStatusGroup.IN_PROGRESS,
          reporterId = UUID.randomUUID(),
          assigneeId = null,
          priorityApiId = null,
          reporterApiId = PublicId.new("usr"),
          assigneeApiId = null,
          sprintApiId = null,
          properties = JsonObject(emptyMap()),
          createdAt = now,
          updatedAt = now,
        )
      val command =
        ReassignSprintBatchCommand(
          tenantId = tenantId,
          projectId = projectId,
          sourceSprintId = UUID.randomUUID(),
          targetSprintId = null,
          disposition = SprintCloseDisposition.BACKLOG,
          actorUserId = UUID.randomUUID(),
          operationId = "sop_1",
        )

      appender.appendSprintChanged(workItem, command, "spr_source", null)

      verify {
        outbox.append(
          spec = WorkItemSprintDomainEvents.SprintChanged,
          key = workItem.apiId.value,
          payload =
            match { it.workItemId == workItem.apiId.value && it.sourceSprintId == "spr_source" },
          metadata = EventMetadata(tenantId = tenantId.toString()),
        )
      }
    }
  })
