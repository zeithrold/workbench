package ink.doa.workbench.web.workitem

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.model.WorkItemCommentFormMeta
import ink.doa.workbench.core.workitem.model.WorkItemCreateFormOption
import ink.doa.workbench.core.workitem.model.WorkItemFormFieldMeta
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
import ink.doa.workbench.core.workitem.model.WorkItemTransitionOption
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.JsonObject

class ProjectWorkItemResponsesTest :
  StringSpec({
    val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")

    "work item response maps record fields" {
      val record =
        WorkItemRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("iss"),
          tenantId = UUID.randomUUID(),
          projectId = UUID.randomUUID(),
          issueTypeApiId = PublicId.new("ity"),
          issueTypeConfigApiId = PublicId.new("itc"),
          key = "WB-1",
          title = "First issue",
          description = "Details",
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

      WorkItemResponse.from(record).key shouldBe "WB-1"
    }

    "transition response maps option metadata" {
      val option =
        WorkItemTransitionOption(
          id = PublicId.new("wft"),
          name = "Start",
          fromStatusId = null,
          toStatusId = PublicId.new("sts"),
          enabled = true,
          fields = JsonObject(emptyMap()),
          fieldMeta =
            listOf(
              WorkItemFormFieldMeta(
                path = "title",
                editable = true,
                participation = "required",
              )
            ),
          commentMeta =
            WorkItemCommentFormMeta(
              participation = "optional",
              editable = true,
              defaultTemplate = "Done",
            ),
        )

      val response = WorkItemTransitionResponse.from(option)
      response.fieldMeta.single().path shouldBe "title"
      response.commentMeta?.defaultTemplate shouldBe "Done"
    }

    "create form response maps option fields" {
      val option =
        WorkItemCreateFormOption(
          issueTypeId = PublicId.new("ity"),
          initialStatusId = PublicId.new("sts"),
          fields = JsonObject(emptyMap()),
          editableFields = listOf("title"),
          fieldMeta =
            listOf(
              WorkItemFormFieldMeta(path = "title", editable = true, participation = "required")
            ),
        )

      WorkItemCreateFormResponse.from(option).editableFields shouldBe listOf("title")
    }

    "work item response maps assignee and priority refs" {
      val record =
        WorkItemRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("iss"),
          tenantId = UUID.randomUUID(),
          projectId = UUID.randomUUID(),
          issueTypeApiId = PublicId.new("ity"),
          issueTypeConfigApiId = PublicId.new("itc"),
          key = "WB-2",
          title = "Assigned",
          description = null,
          statusId = UUID.randomUUID(),
          statusApiId = PublicId.new("sts"),
          statusGroup = WorkItemStatusGroup.IN_PROGRESS,
          reporterId = UUID.randomUUID(),
          assigneeId = UUID.randomUUID(),
          priorityApiId = PublicId.new("pri"),
          reporterApiId = PublicId.new("usr"),
          assigneeApiId = PublicId.new("asg"),
          sprintApiId = PublicId.new("spr"),
          properties = JsonObject(emptyMap()),
          createdAt = now,
          updatedAt = now,
        )

      val response = WorkItemResponse.from(record)
      response.assigneeId shouldBe record.assigneeApiId?.value
      response.priorityId shouldBe record.priorityApiId?.value
      response.sprintId shouldBe record.sprintApiId?.value
    }
  })
