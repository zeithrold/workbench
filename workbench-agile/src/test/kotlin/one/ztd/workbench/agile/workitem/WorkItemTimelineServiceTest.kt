package one.ztd.workbench.agile.workitem

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
import one.ztd.workbench.agile.workitem.activity.WorkItemActivityEntityRef
import one.ztd.workbench.agile.workitem.activity.WorkItemActivityPayload
import one.ztd.workbench.agile.workitem.activity.WorkItemActivityStatusRef
import one.ztd.workbench.agile.workitem.activity.WorkItemActivityStatusSnapshot
import one.ztd.workbench.agile.workitem.activity.WorkItemCreatedPayload
import one.ztd.workbench.agile.workitem.model.WorkItemRecord
import one.ztd.workbench.agile.workitem.model.WorkItemStatusGroup
import one.ztd.workbench.agile.workitem.stream.WorkItemEventRecord
import one.ztd.workbench.agile.workitem.stream.WorkItemEventSourceType
import one.ztd.workbench.agile.workitem.stream.WorkItemEventType
import one.ztd.workbench.agile.workitem.timeline.WorkItemTimelineEntry
import one.ztd.workbench.agile.workitem.timeline.WorkItemTimelinePage
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.ids.PublicId

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
