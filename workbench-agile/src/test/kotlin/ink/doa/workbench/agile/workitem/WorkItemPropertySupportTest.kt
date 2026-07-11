package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.IssueTypeConfigPropertyRecord
import ink.doa.workbench.core.workitem.model.IssueTypeConfigRecord
import ink.doa.workbench.core.workitem.model.UpdateWorkItemCommand
import ink.doa.workbench.core.workitem.model.WorkItemConfigScope
import ink.doa.workbench.core.workitem.model.WorkItemPropertyDataType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class WorkItemPropertySupportTest :
  StringSpec({
    val tenantId = UUID.randomUUID()
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val configId = UUID.randomUUID()
    val propertyId = UUID.randomUUID()
    val config =
      IssueTypeConfigDetails(
        config =
          IssueTypeConfigRecord(
            id = configId,
            apiId = PublicId.new("itc"),
            tenantId = tenantId,
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
            validFrom = now,
            validTo = null,
            createdBy = null,
            createdAt = now,
            updatedAt = now,
            createFields = JsonObject(emptyMap()),
          ),
        statuses = emptyList(),
        properties =
          listOf(
            IssueTypeConfigPropertyRecord(
              id = UUID.randomUUID(),
              tenantId = tenantId,
              issueTypeConfigId = configId,
              propertyId = propertyId,
              propertyApiId = PublicId.new("fld"),
              code = "points",
              name = "Points",
              dataType = WorkItemPropertyDataType.NUMBER,
              validationOverride = JsonObject(emptyMap()),
              rank = 1,
              displayConfig = JsonObject(emptyMap()),
            )
          ),
      )

    "normalizeProperties maps configured property by code" {
      val values =
        WorkItemPropertySupport.normalizeProperties(
          config = config,
          input = mapOf("points" to JsonPrimitive(5)),
        )

      values.single().code shouldBe "points"
      values.single().value shouldBe JsonPrimitive(5)
    }

    "normalizeProperties rejects unavailable property keys" {
      shouldThrow<InvalidRequestException> {
        WorkItemPropertySupport.normalizeProperties(
          config = config,
          input = mapOf("unknown" to JsonPrimitive("x")),
        )
      }
    }

    "filterPropertyInputs removes system template fields" {
      WorkItemPropertySupport.run {
        mapOf(
            "title" to JsonPrimitive("Issue"),
            "points" to JsonPrimitive(3),
          )
          .filterPropertyInputs()
          .keys shouldContainExactly listOf("points")
      }
    }

    "createFieldInputs includes system and custom fields" {
      val inputs =
        WorkItemPropertySupport.createFieldInputs(
          CreateWorkItemCommand(
            tenantId = tenantId,
            projectId = UUID.randomUUID(),
            issueTypeApiId = "ity_bug",
            title = "Bug",
            description = richText("Details"),
            assigneeApiId = "usr_assignee",
            reporterId = UUID.randomUUID(),
            actorUserId = UUID.randomUUID(),
            properties = mapOf("points" to JsonPrimitive(1)),
          )
        )

      inputs["title"] shouldBe JsonPrimitive("Bug")
      inputs["description"] shouldBe richText("Details").content
      inputs["assignee"] shouldBe JsonPrimitive("usr_assignee")
      inputs["points"] shouldBe JsonPrimitive(1)
    }

    "applyDescriptionProcessing validates a document and derives plain text" {
      val updated =
        WorkItemPropertySupport.applyDescriptionProcessing(
          UpdateWorkItemCommand(
            tenantId = tenantId,
            projectId = UUID.randomUUID(),
            workItemApiId = "iss_bug",
            actorUserId = UUID.randomUUID(),
            description = richText("Hello world"),
          )
        )

      updated.descriptionPlainText shouldBe "Hello world"
      updated.description shouldBe richText("Hello world")
    }
  })
