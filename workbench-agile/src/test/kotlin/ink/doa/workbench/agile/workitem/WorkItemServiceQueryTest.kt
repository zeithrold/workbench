package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.IssueTypeConfigRepository
import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.JsonObject

class WorkItemServiceQueryTest :
  StringSpec({
    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()

    "get and list delegate to repository" {
      val repository = mockk<WorkItemRepository>()
      val createParentGuard = mockk<WorkItemCreateParentGuard>(relaxed = true)
      val configs = mockk<IssueTypeConfigRepository>(relaxed = true)
      val mutationSupport = mockk<WorkItemMutationSupport>(relaxed = true)
      val fieldMutation = mockk<WorkItemFieldMutationFacade>(relaxed = true)
      val now = OffsetDateTime.now(ZoneOffset.UTC)
      val record =
        WorkItemRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("iss"),
          tenantId = tenantId,
          projectId = projectId,
          issueTypeApiId = PublicId.new("typ"),
          issueTypeConfigApiId = PublicId.new("itc"),
          key = "WB-1",
          title = "Issue",
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
      coEvery { repository.findByApiId(tenantId, projectId, "WB-1") } returns record
      coEvery { repository.listByProject(tenantId, projectId, 50, 0L) } returns listOf(record)
      val service =
        WorkItemService(
          repository,
          configs,
          createParentGuard,
          mutationSupport,
          mockk<WorkItemActivityEnqueueSupport>(relaxed = true),
          fieldMutation,
        )

      service.get(tenantId, projectId, "WB-1") shouldBe record
      service.list(tenantId, projectId).single().key shouldBe "WB-1"
    }
  })
