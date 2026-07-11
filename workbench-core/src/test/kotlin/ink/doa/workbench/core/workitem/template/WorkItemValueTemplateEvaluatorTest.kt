package ink.doa.workbench.core.workitem.template

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.IssueTypeConfigPropertyRecord
import ink.doa.workbench.core.workitem.model.IssueTypeConfigRecord
import ink.doa.workbench.core.workitem.model.WorkItemConfigScope
import ink.doa.workbench.core.workitem.model.WorkItemPropertyDataType
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class WorkItemValueTemplateEvaluatorTest :
  StringSpec({
    val parser = WorkItemValueTemplateParser()
    val evaluator =
      WorkItemValueTemplateEvaluator(
        Clock.fixed(Instant.parse("2026-07-04T10:15:30Z"), ZoneOffset.UTC)
      )
    val config = config()
    val context =
      WorkItemValueTemplateContext(
        tenantId = UUID.randomUUID(),
        projectId = UUID.randomUUID(),
        currentUserApiId = "usr_current",
        currentProjectApiId = "prj_current",
        actorUserId = UUID.randomUUID(),
        currentProperties = mapOf("resolution" to JsonPrimitive("fixed")),
      )

    "evaluates variables, copies, clear, and relative dates" {
      val template =
        parser.parse(
          """
          {
            "version": 1,
            "resource": "work_item",
            "target": "transition",
            "values": {
              "assignee": { "var": "user.currentUser" },
              "property.dueDate": {
                "relativeDate": {
                  "amount": 3,
                  "unit": "day",
                  "direction": "future",
                  "anchor": "date.today"
                }
              },
              "property.resolvedAt": { "var": "date.now" },
              "property.copyOfResolution": { "copy": "property.resolution" },
              "property.optionalText": { "clear": true }
            }
          }
          """
            .trimIndent()
        )

      evaluator.evaluate(template, config, context) shouldBe
        mapOf(
          "assignee" to JsonPrimitive("usr_current"),
          "dueDate" to JsonPrimitive("2026-07-07"),
          "resolvedAt" to JsonPrimitive("2026-07-04T10:15:30Z"),
          "copyOfResolution" to JsonPrimitive("fixed"),
          "optionalText" to JsonNull,
        )
    }

    "evaluates date.startOfWeek and date.endOfWeek variables" {
      val template =
        parser.parse(
          """
          {
            "version": 1,
            "resource": "work_item",
            "target": "transition",
            "values": {
              "property.dueDate": { "var": "date.startOfWeek" },
              "property.resolvedAt": { "var": "date.endOfWeek" }
            }
          }
          """
            .trimIndent()
        )

      evaluator.evaluate(template, config, context) shouldBe
        mapOf(
          "dueDate" to JsonPrimitive("2026-06-29"),
          "resolvedAt" to JsonPrimitive("2026-07-05"),
        )
    }

    "evaluatePropertyDefault evaluates expression for a property" {
      val dueDateProperty = config.properties.single { it.code == "dueDate" }
      val expression =
        TemplateValueExpression.RelativeDate(
          amount = 2,
          unit = TemplateRelativeDateUnit.WEEK,
          direction = TemplateDateDirection.FUTURE,
          anchor = "date.today",
        )

      evaluator.evaluatePropertyDefault(dueDateProperty, expression, config, context) shouldBe
        JsonPrimitive("2026-07-18")
    }

    "unknown variable throws InvalidRequestException" {
      val expression = TemplateValueExpression.Variable("user.unknown")

      shouldThrow<InvalidRequestException> {
          evaluator.evaluateExpression(expression, null, context)
        }
        .message shouldBe "Unknown work item value template variable: user.unknown"
    }

    "evaluates relative dates with week and month units" {
      val weekTemplate =
        parser.parse(
          """
          {
            "version": 1,
            "resource": "work_item",
            "target": "transition",
            "values": {
              "property.dueDate": {
                "relativeDate": {
                  "amount": 1,
                  "unit": "week",
                  "direction": "future",
                  "anchor": "date.today"
                }
              }
            }
          }
          """
            .trimIndent()
        )
      val monthTemplate =
        parser.parse(
          """
          {
            "version": 1,
            "resource": "work_item",
            "target": "transition",
            "values": {
              "property.dueDate": {
                "relativeDate": {
                  "amount": 1,
                  "unit": "month",
                  "direction": "past",
                  "anchor": "date.startOfWeek"
                }
              }
            }
          }
          """
            .trimIndent()
        )

      evaluator.evaluate(weekTemplate, config, context) shouldBe
        mapOf("dueDate" to JsonPrimitive("2026-07-11"))

      evaluator.evaluate(monthTemplate, config, context) shouldBe
        mapOf("dueDate" to JsonPrimitive("2026-05-29"))
    }

    "evaluates project and date.today variables" {
      val template =
        parser.parse(
          """
          {
            "version": 1,
            "resource": "work_item",
            "target": "transition",
            "values": {
              "sprint": { "var": "project.currentProject" },
              "property.dueDate": { "var": "date.today" }
            }
          }
          """
            .trimIndent()
        )

      evaluator.evaluate(template, config, context) shouldBe
        mapOf(
          "sprint" to JsonPrimitive("prj_current"),
          "dueDate" to JsonPrimitive("2026-07-04"),
        )
    }

    "evaluates relative datetime with date.now anchor" {
      val resolvedAtProperty = config.properties.single { it.code == "resolvedAt" }
      val expression =
        TemplateValueExpression.RelativeDate(
          amount = 1,
          unit = TemplateRelativeDateUnit.DAY,
          direction = TemplateDateDirection.FUTURE,
          anchor = "date.now",
        )

      evaluator.evaluatePropertyDefault(resolvedAtProperty, expression, config, context) shouldBe
        JsonPrimitive("2026-07-05T10:15:30Z")
    }

    "evaluates year unit and endOfWeek anchor relative dates" {
      val yearTemplate =
        parser.parse(
          """
          {
            "version": 1,
            "resource": "work_item",
            "target": "transition",
            "values": {
              "property.dueDate": {
                "relativeDate": {
                  "amount": 1,
                  "unit": "year",
                  "direction": "past",
                  "anchor": "date.endOfWeek"
                }
              }
            }
          }
          """
            .trimIndent()
        )

      evaluator.evaluate(yearTemplate, config, context) shouldBe
        mapOf("dueDate" to JsonPrimitive("2025-07-05"))
    }

    "workItem.previous.title resolves like current copy path" {
      val workItemContext =
        context.copy(
          workItem =
            workItemRecord(
              WorkItemRecordFixtures(
                title = "Previous title",
                projectId = context.projectId,
                tenantId = context.tenantId,
              )
            )
        )
      val expression = TemplateValueExpression.Variable("workItem.previous.title")

      evaluator.evaluateExpression(expression, null, workItemContext) shouldBe
        JsonPrimitive("Previous title")
    }

    "relative date rejects non-positive amount and unknown anchor" {
      shouldThrow<InvalidRequestException> {
          evaluator.evaluateExpression(
            TemplateValueExpression.RelativeDate(
              amount = 0,
              unit = TemplateRelativeDateUnit.DAY,
              direction = TemplateDateDirection.FUTURE,
              anchor = "date.today",
            ),
            null,
            context,
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_TEMPLATE_RELATIVE_DATE_AMOUNT_POSITIVE

      shouldThrow<InvalidRequestException> {
          evaluator.evaluateExpression(
            TemplateValueExpression.RelativeDate(
              amount = 1,
              unit = TemplateRelativeDateUnit.DAY,
              direction = TemplateDateDirection.FUTURE,
              anchor = "date.unknown",
            ),
            null,
            context,
          )
        }
        .message shouldBe "Unknown relative date anchor: date.unknown"
    }

    "copies system fields from work item context" {
      val workItemContext =
        context.copy(
          workItem =
            workItemRecord(
              WorkItemRecordFixtures(
                title = "Fix login regression",
                description = "Steps to reproduce",
                assigneeApiId = PublicId.new("usr"),
                priorityApiId = PublicId.new("pri"),
                sprintApiId = PublicId.new("spr"),
                projectId = context.projectId,
                tenantId = context.tenantId,
              )
            )
        )
      val workItem = checkNotNull(workItemContext.workItem)
      val template =
        parser.parse(
          """
          {
            "version": 1,
            "resource": "work_item",
            "target": "transition",
            "values": {
              "description": { "copy": "description" },
              "assignee": { "copy": "assignee" },
              "priority": { "copy": "priority" },
              "sprint": { "copy": "sprint" }
            }
          }
          """
            .trimIndent()
        )

      evaluator.evaluate(template, config, workItemContext) shouldBe
        mapOf(
          "description" to checkNotNull(workItem.description).content,
          "assignee" to JsonPrimitive(checkNotNull(workItem.assigneeApiId).value),
          "priority" to JsonPrimitive(checkNotNull(workItem.priorityApiId).value),
          "sprint" to JsonPrimitive(checkNotNull(workItem.sprintApiId).value),
        )
    }

    "resolveCopy reads property values by apiId from current properties" {
      val fieldApiId = "fld_copy"
      val workItemContext =
        context.copy(currentProperties = mapOf(fieldApiId to JsonPrimitive("from-api-id")))

      evaluator.evaluateExpression(
        TemplateValueExpression.Copy(TemplateField.Property(apiId = fieldApiId, code = null)),
        null,
        workItemContext,
      ) shouldBe JsonPrimitive("from-api-id")
    }

    "evaluateExpression returns literal values unchanged" {
      val literal = JsonPrimitive("literal")

      evaluator.evaluateExpression(TemplateValueExpression.Literal(literal), null, context) shouldBe
        literal
    }

    "workItem.current.title copies title when work item is in context" {
      val workItemContext =
        context.copy(
          workItem =
            workItemRecord(
              WorkItemRecordFixtures(
                title = "Fix login regression",
                projectId = context.projectId,
                tenantId = context.tenantId,
              )
            )
        )
      val template =
        parser.parse(
          """
          {
            "version": 1,
            "resource": "work_item",
            "target": "transition",
            "values": {
              "title": { "var": "workItem.current.title" },
              "property.copyOfResolution": { "copy": "title" }
            }
          }
          """
            .trimIndent()
        )

      evaluator.evaluate(template, config, workItemContext) shouldBe
        mapOf(
          "title" to JsonPrimitive("Fix login regression"),
          "copyOfResolution" to JsonPrimitive("Fix login regression"),
        )
    }
  })

private fun config(): IssueTypeConfigDetails {
  val configId = UUID.randomUUID()
  return IssueTypeConfigDetails(
    config =
      IssueTypeConfigRecord(
        id = configId,
        apiId = PublicId.new("itc"),
        tenantId = UUID.randomUUID(),
        scope = WorkItemConfigScope.TENANT,
        projectId = null,
        issueTypeId = UUID.randomUUID(),
        issueTypeApiId = PublicId.new("typ"),
        workflowId = UUID.randomUUID(),
        workflowApiId = PublicId.new("wfl"),
        version = 1,
        nameOverride = null,
        iconOverride = null,
        colorOverride = null,
        rank = 100,
        isActive = true,
        validFrom = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
        validTo = null,
        createdBy = null,
        createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
        updatedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
        createFields = kotlinx.serialization.json.JsonObject(emptyMap()),
      ),
    statuses = emptyList(),
    properties =
      listOf(
        property(configId, "dueDate", WorkItemPropertyDataType.DATE),
        property(configId, "resolvedAt", WorkItemPropertyDataType.DATETIME),
        property(configId, "resolution", WorkItemPropertyDataType.TEXT),
        property(configId, "copyOfResolution", WorkItemPropertyDataType.TEXT),
        property(configId, "optionalText", WorkItemPropertyDataType.TEXT),
        property(configId, "requiredText", WorkItemPropertyDataType.TEXT),
      ),
  )
}

private data class WorkItemRecordFixtures(
  val title: String,
  val projectId: UUID,
  val tenantId: UUID,
  val description: String? = null,
  val assigneeApiId: PublicId? = null,
  val priorityApiId: PublicId? = null,
  val sprintApiId: PublicId? = null,
  val properties: JsonObject = JsonObject(emptyMap()),
)

private fun workItemRecord(fixtures: WorkItemRecordFixtures): WorkItemRecord {
  val now = OffsetDateTime.parse("2026-07-04T10:15:30Z")
  return WorkItemRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("wki"),
    tenantId = fixtures.tenantId,
    projectId = fixtures.projectId,
    issueTypeApiId = PublicId.new("typ"),
    issueTypeConfigApiId = PublicId.new("itc"),
    key = "PROJ-1",
    title = fixtures.title,
    description =
      ink.doa.workbench.core.workitem.richtext.RichTextProcessor.fromPlainText(
        fixtures.description
      ),
    statusId = UUID.randomUUID(),
    statusApiId = PublicId.new("sts"),
    statusGroup = WorkItemStatusGroup.TODO,
    reporterId = UUID.randomUUID(),
    assigneeId = fixtures.assigneeApiId?.let { UUID.randomUUID() },
    priorityApiId = fixtures.priorityApiId,
    reporterApiId = PublicId.new("usr"),
    assigneeApiId = fixtures.assigneeApiId,
    sprintApiId = fixtures.sprintApiId,
    properties = fixtures.properties,
    createdAt = now,
    updatedAt = now,
  )
}

private fun property(
  configId: UUID,
  code: String,
  dataType: WorkItemPropertyDataType,
): IssueTypeConfigPropertyRecord =
  IssueTypeConfigPropertyRecord(
    id = UUID.randomUUID(),
    tenantId = UUID.randomUUID(),
    issueTypeConfigId = configId,
    propertyId = UUID.randomUUID(),
    propertyApiId = PublicId.new("fld"),
    code = code,
    name = code,
    dataType = dataType,
    validationOverride = kotlinx.serialization.json.JsonObject(emptyMap()),
    rank = 100,
    displayConfig = kotlinx.serialization.json.JsonObject(emptyMap()),
  )
