package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.WorkItemActivityRepository
import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.activity.WorkItemActivityListPage
import ink.doa.workbench.core.workitem.activity.WorkItemActivityPageInfo
import ink.doa.workbench.core.workitem.activity.WorkItemActivityPayload
import ink.doa.workbench.core.workitem.activity.WorkItemActivityRecord
import ink.doa.workbench.core.workitem.activity.WorkItemActivitySourceType
import ink.doa.workbench.core.workitem.activity.WorkItemActivityType
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

class WorkItemActivityServiceTest :
  StringSpec({
    val activities = mockk<WorkItemActivityRepository>()
    val workItems = mockk<WorkItemRepository>()
    val service = WorkItemActivityService(activities, workItems)

    "list delegates to repository when work item exists" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val issue = sampleIssue(tenantId, projectId)
      val page =
        WorkItemActivityListPage(
          items =
            listOf(
              WorkItemActivityRecord(
                id = UUID.randomUUID(),
                apiId = PublicId.new("act"),
                tenantId = tenantId,
                projectId = projectId,
                workItemId = issue.id,
                workItemApiId = issue.apiId,
                actorUserId = issue.reporterId,
                actorApiId = issue.reporterApiId,
                actorDisplayName = "Ada",
                activityType = WorkItemActivityType.CREATED,
                occurredAt = OffsetDateTime.parse("2026-07-05T12:00:00Z"),
                summary = "Created",
                payload = WorkItemActivityPayload.Unknown(JsonObject(emptyMap())),
                sourceType = WorkItemActivitySourceType.USER,
                createdAt = OffsetDateTime.parse("2026-07-05T12:00:00Z"),
              )
            ),
          page = WorkItemActivityPageInfo(limit = 50, nextBefore = null),
        )

      coEvery { workItems.findByApiId(tenantId, projectId, issue.apiId.value) } returns issue
      coEvery { activities.listByWorkItem(any()) } returns page

      val result = runBlocking { service.list(tenantId, projectId, issue.apiId.value, limit = 50) }

      result shouldBe page
      coVerify { activities.listByWorkItem(any()) }
    }

    "list rejects missing work item" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      coEvery { workItems.findByApiId(tenantId, projectId, "iss_missing") } returns null

      shouldThrow<ResourceNotFoundException> {
          runBlocking { service.list(tenantId, projectId, "iss_missing") }
        }
        .errorCode shouldBe WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND
    }
  })

private fun sampleIssue(tenantId: UUID, projectId: UUID): WorkItemRecord {
  val now = OffsetDateTime.parse("2026-07-05T12:00:00Z")
  val userId = UUID.randomUUID()
  return WorkItemRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("iss"),
    tenantId = tenantId,
    projectId = projectId,
    issueTypeApiId = PublicId.new("ity"),
    issueTypeConfigApiId = PublicId.new("itc"),
    key = "WB-1",
    title = "Issue",
    description = null,
    statusId = UUID.randomUUID(),
    statusApiId = PublicId.new("sts"),
    statusGroup = WorkItemStatusGroup.TODO,
    reporterId = userId,
    assigneeId = null,
    priorityApiId = null,
    reporterApiId = PublicId.new("usr"),
    assigneeApiId = null,
    sprintApiId = null,
    properties = JsonObject(emptyMap()),
    createdAt = now,
    updatedAt = now,
  )
}
