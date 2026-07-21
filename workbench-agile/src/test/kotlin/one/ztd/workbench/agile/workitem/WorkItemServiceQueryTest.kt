package one.ztd.workbench.agile.workitem

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import one.ztd.workbench.agile.testfixtures.AgileWorkItemFixtures
import one.ztd.workbench.agile.workitem.model.WorkItemRecord
import one.ztd.workbench.agile.workitem.model.WorkItemStatusGroup
import one.ztd.workbench.identity.UserRepository
import one.ztd.workbench.kernel.common.ids.PublicId

class WorkItemServiceQueryTest :
  StringSpec({
    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()

    "get delegates to the read model service" {
      val repository = mockk<WorkItemRepository>()
      val createParentGuard = mockk<WorkItemCreateParentGuard>(relaxed = true)
      val configs = mockk<IssueTypeConfigRepository>(relaxed = true)
      val mutationSupport = mockk<WorkItemMutationSupport>(relaxed = true)
      val fieldPipeline = mockk<WorkItemFieldMutationPipeline>(relaxed = true)
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
      val hit = AgileWorkItemFixtures.searchHit(record)
      coEvery { mutationSupport.read(tenantId, projectId, "WB-1") } returns hit
      val users = mockk<UserRepository>(relaxed = true)
      val service =
        WorkItemService(
          repository,
          configs,
          users,
          createParentGuard,
          mutationSupport,
          WorkItemUpdateSupport(fieldPipeline, mockk(relaxed = true)),
        )

      service.get(tenantId, projectId, "WB-1") shouldBe hit
    }
  })
