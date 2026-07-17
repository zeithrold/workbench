package one.ztd.workbench.web.workitem

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import one.ztd.workbench.agile.workitem.model.WorkItemCommentFormMeta
import one.ztd.workbench.agile.workitem.model.WorkItemCreateFormOption
import one.ztd.workbench.agile.workitem.model.WorkItemFormFieldMeta
import one.ztd.workbench.agile.workitem.model.WorkItemPrioritySummary
import one.ztd.workbench.agile.workitem.model.WorkItemPropertyPresentation
import one.ztd.workbench.agile.workitem.model.WorkItemPropertySummary
import one.ztd.workbench.agile.workitem.model.WorkItemRecord
import one.ztd.workbench.agile.workitem.model.WorkItemSprintSummary
import one.ztd.workbench.agile.workitem.model.WorkItemStatusGroup
import one.ztd.workbench.agile.workitem.model.WorkItemTransitionOption
import one.ztd.workbench.agile.workitem.query.ConditionNode
import one.ztd.workbench.agile.workitem.query.QueryField
import one.ztd.workbench.agile.workitem.query.QueryOperator
import one.ztd.workbench.agile.workitem.query.QueryValue
import one.ztd.workbench.agile.workitem.query.WorkItemGroupLabel
import one.ztd.workbench.agile.workitem.richtext.RichTextProcessor
import one.ztd.workbench.kernel.common.ids.PublicId

class ProjectWorkItemResponsesTest :
  StringSpec({
    val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")

    "work item response maps record fields" {
      val tenantId = java.util.UUID.randomUUID()
      val record = sampleRecord(tenantId, now).copy(key = "WB-1", title = "First issue")

      WorkItemResponse.from(workItemReadModel(record)).key shouldBe "WB-1"
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

    "work item response exposes summaries without legacy display ids" {
      val tenantId = java.util.UUID.randomUUID()
      val record = sampleRecord(tenantId, now)
      val response = WorkItemResponse.from(workItemReadModel(record))

      response.issueType.name shouldBe "Task"
      response.status.group shouldBe "in_progress"
      response.reporter.displayName shouldBe "Reporter"
      response.assignee?.displayName shouldBe "Assignee"
    }

    "work item response maps every renderable summary and property" {
      val tenantId = java.util.UUID.randomUUID()
      val record =
        sampleRecord(tenantId, now).copy(description = RichTextProcessor.fromPlainText("Details"))
      val propertyId = PublicId.new("fld").value
      val hit =
        workItemReadModel(record)
          .copy(
            priority =
              WorkItemPrioritySummary(
                id = PublicId.new("pri").value,
                code = "high",
                name = "High",
                icon = "arrow-up",
                color = "#ef4444",
              ),
            sprint =
              WorkItemSprintSummary(
                id = PublicId.new("spr").value,
                name = "Sprint 24",
                status = "ACTIVE",
                startAt = now,
                endAt = now.plusDays(14),
              ),
            properties =
              mapOf(
                propertyId to
                  WorkItemPropertyPresentation(
                    property =
                      WorkItemPropertySummary(
                        id = propertyId,
                        code = "severity",
                        name = "Severity",
                        dataType = "single_select",
                        array = false,
                      ),
                    value = JsonPrimitive("opt_critical"),
                    displayValue =
                      JsonObject(
                        mapOf(
                          "id" to JsonPrimitive("opt_critical"),
                          "label" to JsonPrimitive("Critical"),
                        )
                      ),
                  )
              ),
            groupKey =
              ConditionNode.Predicate(
                field = QueryField.System("statusGroup"),
                op = QueryOperator.EQ,
                value = QueryValue.Literal(JsonPrimitive("in_progress")),
              ),
            groupLabel = WorkItemGroupLabel.Text("In Progress"),
          )

      val response = WorkItemResponse.from(hit)

      response.description?.content?.size()?.let { it > 0 } shouldBe true
      response.priority?.icon shouldBe "arrow-up"
      response.sprint?.name shouldBe "Sprint 24"
      response.properties.getValue(propertyId).property.code shouldBe "severity"
      response.properties.getValue(propertyId).displayValue shouldBe
        hit.properties.getValue(propertyId).displayValue
      response.groupKey?.get("field") shouldBe JsonPrimitive("statusGroup")
      (response.groupLabel as WorkItemGroupLabelTextResponse).text shouldBe "In Progress"
    }

    "group label response maps message metadata" {
      val response =
        WorkItemGroupLabelResponse.from(
          WorkItemGroupLabel.Message(
            code = "work_item.group.empty",
            args = mapOf("field" to "Sprint"),
            defaultMessage = "No Sprint",
          )
        ) as WorkItemGroupLabelMessageResponse

      response.args shouldBe mapOf("field" to "Sprint")
      response.defaultMessage shouldBe "No Sprint"
    }
  })

private fun sampleRecord(tenantId: java.util.UUID, now: OffsetDateTime): WorkItemRecord =
  WorkItemRecord(
    id = java.util.UUID.randomUUID(),
    apiId = PublicId.new("iss"),
    tenantId = tenantId,
    projectId = java.util.UUID.randomUUID(),
    issueTypeApiId = PublicId.new("typ"),
    issueTypeConfigApiId = PublicId.new("itc"),
    key = "WB-2",
    title = "Assigned",
    description = null,
    statusId = java.util.UUID.randomUUID(),
    statusApiId = PublicId.new("sts"),
    statusGroup = WorkItemStatusGroup.IN_PROGRESS,
    reporterId = java.util.UUID.randomUUID(),
    assigneeId = java.util.UUID.randomUUID(),
    priorityApiId = null,
    reporterApiId = PublicId.new("usr"),
    assigneeApiId = PublicId.new("usr"),
    sprintApiId = null,
    properties = JsonObject(emptyMap()),
    createdAt = now,
    updatedAt = now,
  )
