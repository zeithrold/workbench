package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.WorkItemTimelineRepository
import ink.doa.workbench.core.workitem.activity.WorkItemActivityEntityRef
import ink.doa.workbench.core.workitem.activity.WorkItemActivityPayload
import ink.doa.workbench.core.workitem.activity.WorkItemActivityStatusRef
import ink.doa.workbench.core.workitem.activity.WorkItemActivityStatusSnapshot
import ink.doa.workbench.core.workitem.activity.WorkItemCreatedPayload
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
import ink.doa.workbench.core.workitem.stream.WorkItemEventRecord
import ink.doa.workbench.core.workitem.stream.WorkItemEventSourceType
import ink.doa.workbench.core.workitem.stream.WorkItemEventType
import ink.doa.workbench.core.workitem.timeline.WorkItemTimelineEntry
import ink.doa.workbench.core.workitem.timeline.WorkItemTimelinePage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.JsonObject

class WorkItemTimelineServiceTest :
  StringSpec({
    val timeline = mockk<WorkItemTimelineRepository>()
    val workItems = mockk<WorkItemRepository>()
    val service = WorkItemTimelineService(timeline, workItems)

    "list returns timeline page for existing work item" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val workItemId = UUID.randomUUID()
      val now = OffsetDateTime.now(ZoneOffset.UTC)
      coEvery { workItems.findByApiId(tenantId, projectId, "iss_01") } returns
        workItemRecord(tenantId, projectId, workItemId, now)
      coEvery { timeline.listByWorkItem(any()) } returns
        WorkItemTimelinePage(
          items =
            listOf(
              WorkItemTimelineEntry.Event(
                WorkItemEventRecord(
                  id = UUID.randomUUID(),
                  apiId = PublicId.new("evt"),
                  tenantId = tenantId,
                  projectId = projectId,
                  workItemId = workItemId,
                  workItemApiId = PublicId.new("iss"),
                  sequence = 1,
                  eventType = WorkItemEventType.CREATED,
                  actorUserId = null,
                  actorApiId = null,
                  actorDisplayName = null,
                  occurredAt = now,
                  summary = null,
                  payload =
                    WorkItemActivityPayload.Created(
                      WorkItemCreatedPayload(
                        status =
                          WorkItemActivityStatusSnapshot(
                            to = WorkItemActivityStatusRef("sts_01", "Todo", "todo")
                          ),
                        issueType = WorkItemActivityEntityRef("ity_01", "Task"),
                      )
                    ),
                  sourceType = WorkItemEventSourceType.USER,
                  createdAt = now,
                )
              )
            ),
          nextCursor = null,
        )

      val page = service.list(tenantId, projectId, "iss_01", limit = 50, cursorToken = null)

      page.items shouldHaveSize 1
      coVerify { timeline.listByWorkItem(match { it.workItemApiId == "iss_01" && it.limit == 50 }) }
    }

    "list throws when work item is missing" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      coEvery { workItems.findByApiId(tenantId, projectId, "iss_missing") } returns null

      shouldThrow<ResourceNotFoundException> {
        service.list(tenantId, projectId, "iss_missing")
      }
    }
  })

private fun workItemRecord(
  tenantId: UUID,
  projectId: UUID,
  workItemId: UUID,
  now: OffsetDateTime,
): WorkItemRecord =
  WorkItemRecord(
    id = workItemId,
    apiId = PublicId.new("iss"),
    tenantId = tenantId,
    projectId = projectId,
    issueTypeApiId = PublicId.new("ity"),
    issueTypeConfigApiId = PublicId.new("itc"),
    key = "WB-1",
    title = "Timeline target",
    description = null,
    statusId = UUID.randomUUID(),
    statusApiId = PublicId.new("sts"),
    statusGroup = WorkItemStatusGroup.TODO,
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
