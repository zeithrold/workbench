package ink.doa.workbench.core.workitem.template

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.IssueTypeConfigPropertyRecord
import ink.doa.workbench.core.workitem.model.IssueTypeConfigRecord
import ink.doa.workbench.core.workitem.model.WorkItemConfigScope
import ink.doa.workbench.core.workitem.model.WorkItemPropertyDataType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.JsonNull
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

    "rejects clearing required properties" {
      shouldThrow<InvalidRequestException> {
        evaluator.evaluatePropertyDefault(
          property = config.properties.first { it.code == "requiredText" },
          expression = TemplateValueExpression.Clear,
          config = config,
          context = context,
        )
      }
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
      ),
    statuses = emptyList(),
    properties =
      listOf(
        property(configId, "dueDate", WorkItemPropertyDataType.DATE),
        property(configId, "resolvedAt", WorkItemPropertyDataType.DATETIME),
        property(configId, "resolution", WorkItemPropertyDataType.TEXT),
        property(configId, "copyOfResolution", WorkItemPropertyDataType.TEXT),
        property(configId, "optionalText", WorkItemPropertyDataType.TEXT),
        property(configId, "requiredText", WorkItemPropertyDataType.TEXT, isRequired = true),
      ),
  )
}

private fun property(
  configId: UUID,
  code: String,
  dataType: WorkItemPropertyDataType,
  isRequired: Boolean = false,
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
    isRequired = isRequired,
    defaultValue = null,
    validationOverride = kotlinx.serialization.json.JsonObject(emptyMap()),
    rank = 100,
    displayConfig = kotlinx.serialization.json.JsonObject(emptyMap()),
  )
