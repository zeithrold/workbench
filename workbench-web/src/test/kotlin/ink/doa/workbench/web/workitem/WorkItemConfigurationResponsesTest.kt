package ink.doa.workbench.web.workitem

import com.fasterxml.jackson.databind.ObjectMapper
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.model.EffectiveIssueTypeConfig
import ink.doa.workbench.core.workitem.model.IssueStatusRecord
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.IssueTypeConfigPropertyRecord
import ink.doa.workbench.core.workitem.model.IssueTypeConfigRecord
import ink.doa.workbench.core.workitem.model.IssueTypeConfigStatusRecord
import ink.doa.workbench.core.workitem.model.IssueTypeRecord
import ink.doa.workbench.core.workitem.model.PropertyDefinitionRecord
import ink.doa.workbench.core.workitem.model.WorkItemCommentRecord
import ink.doa.workbench.core.workitem.model.WorkItemConfigScope
import ink.doa.workbench.core.workitem.model.WorkItemPropertyDataType
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
import ink.doa.workbench.core.workitem.model.WorkflowRecord
import ink.doa.workbench.core.workitem.model.WorkflowTransitionRecord
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.JsonObject

class WorkItemConfigurationResponsesTest :
  StringSpec({
    val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")
    val tenantId = UUID.randomUUID()

    "issue status response maps record fields" {
      val record =
        IssueStatusRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("sts"),
          tenantId = tenantId,
          code = "todo",
          name = "To Do",
          statusGroup = WorkItemStatusGroup.TODO,
          rank = 10,
          color = "#fff",
          isTerminal = false,
          isActive = true,
          createdAt = now,
          updatedAt = now,
        )

      IssueStatusResponse.from(record).code shouldBe "todo"
    }

    "property definition response maps record fields" {
      val record =
        PropertyDefinitionRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("fld"),
          tenantId = tenantId,
          code = "points",
          name = "Points",
          description = null,
          dataType = WorkItemPropertyDataType.NUMBER,
          isSystem = false,
          isArray = false,
          validationSchema = JsonObject(emptyMap()),
          searchConfig = JsonObject(emptyMap()),
          isActive = true,
          createdAt = now,
          updatedAt = now,
        )

      PropertyDefinitionResponse.from(record).dataType shouldBe "number"
    }

    "issue type response maps record fields" {
      val record =
        IssueTypeRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("ity"),
          tenantId = tenantId,
          projectId = null,
          scope = WorkItemConfigScope.TENANT,
          code = "story",
          name = "Story",
          description = null,
          icon = null,
          color = null,
          rank = 1,
          isActive = true,
          createdAt = now,
          updatedAt = now,
        )

      IssueTypeResponse.from(record).scope shouldBe "tenant"
    }

    "workflow response maps record fields" {
      val record =
        WorkflowRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("wfl"),
          tenantId = tenantId,
          code = "default",
          name = "Default",
          description = "Primary",
          version = 1,
          isActive = true,
          publishedAt = now,
          createdBy = null,
          createdAt = now,
          updatedAt = now,
        )

      WorkflowResponse.from(record).version shouldBe 1
    }

    "workflow transition response maps record fields" {
      val toStatusId = UUID.randomUUID()
      val record =
        WorkflowTransitionRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("wft"),
          tenantId = tenantId,
          workflowId = UUID.randomUUID(),
          name = "Start",
          fromStatusId = null,
          fromStatusApiId = null,
          toStatusId = toStatusId,
          toStatusApiId = PublicId.new("sts"),
          rank = 1,
          permissionCondition = JsonObject(emptyMap()),
          preconditionAst = JsonObject(emptyMap()),
          fields = JsonObject(emptyMap()),
          isActive = true,
          createdAt = now,
          updatedAt = now,
        )

      WorkflowTransitionResponse.from(record).name shouldBe "Start"
    }

    "issue type config response maps nested details" {
      val config =
        IssueTypeConfigRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("itc"),
          tenantId = tenantId,
          scope = WorkItemConfigScope.TENANT,
          projectId = null,
          issueTypeId = UUID.randomUUID(),
          issueTypeApiId = PublicId.new("ity"),
          workflowId = UUID.randomUUID(),
          workflowApiId = PublicId.new("wfl"),
          version = 1,
          nameOverride = null,
          iconOverride = null,
          colorOverride = null,
          rank = 1,
          isActive = true,
          validFrom = now,
          validTo = null,
          createdBy = null,
          createdAt = now,
          updatedAt = now,
          createFields = JsonObject(emptyMap()),
        )
      val status =
        IssueTypeConfigStatusRecord(
          id = UUID.randomUUID(),
          tenantId = tenantId,
          issueTypeConfigId = config.id,
          statusId = UUID.randomUUID(),
          statusApiId = PublicId.new("sts"),
          code = "todo",
          name = "To Do",
          statusGroup = WorkItemStatusGroup.TODO,
          isInitial = true,
          isTerminal = false,
          rank = 1,
        )
      val property =
        IssueTypeConfigPropertyRecord(
          id = UUID.randomUUID(),
          tenantId = tenantId,
          issueTypeConfigId = config.id,
          propertyId = UUID.randomUUID(),
          propertyApiId = PublicId.new("fld"),
          code = "points",
          name = "Points",
          dataType = WorkItemPropertyDataType.NUMBER,
          validationOverride = JsonObject(emptyMap()),
          rank = 1,
          displayConfig = JsonObject(emptyMap()),
        )

      val response =
        IssueTypeConfigResponse.from(
          IssueTypeConfigDetails(
            config = config,
            statuses = listOf(status),
            properties = listOf(property),
          )
        )

      response.statuses.single().code shouldBe "todo"
      response.properties.single().code shouldBe "points"
    }

    "effective issue type config response maps resolved scope" {
      val config =
        IssueTypeConfigDetails(
          config =
            IssueTypeConfigRecord(
              id = UUID.randomUUID(),
              apiId = PublicId.new("itc"),
              tenantId = tenantId,
              scope = WorkItemConfigScope.PROJECT,
              projectId = UUID.randomUUID(),
              issueTypeId = UUID.randomUUID(),
              issueTypeApiId = PublicId.new("ity"),
              workflowId = UUID.randomUUID(),
              workflowApiId = PublicId.new("wfl"),
              version = 1,
              nameOverride = null,
              iconOverride = null,
              colorOverride = null,
              rank = 1,
              isActive = true,
              validFrom = now,
              validTo = null,
              createdBy = null,
              createdAt = now,
              updatedAt = now,
              createFields = JsonObject(emptyMap()),
            ),
          statuses = emptyList(),
          properties = emptyList(),
        )

      EffectiveIssueTypeConfigResponse.from(
          EffectiveIssueTypeConfig(config = config, resolvedFrom = WorkItemConfigScope.PROJECT)
        )
        .resolvedFrom shouldBe "project"
    }

    "work item comment response maps record fields" {
      val record =
        WorkItemCommentRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("icm"),
          tenantId = tenantId,
          issueId = UUID.randomUUID(),
          authorId = UUID.randomUUID(),
          authorApiId = PublicId.new("usr"),
          body = "Looks good",
          bodyPlainText = "Looks good",
          bodyFormat = "html",
          transitionId = null,
          statusHistoryId = null,
          editedAt = null,
          createdAt = now,
          updatedAt = now,
        )

      WorkItemCommentResponse.from(record).body shouldBe "Looks good"
    }

    "json node helpers convert objects and maps" {
      val mapper = ObjectMapper()
      val node = mapper.readTree("""{"points": 3}""")
      val jsonObject = node.toJsonObject(mapper)
      jsonObject.toMap().keys shouldBe setOf("points")
      null.toJsonObject(mapper).toMap() shouldBe emptyMap()
    }

    "create workflow transition request stores transition fields" {
      val mapper = ObjectMapper()
      val request =
        CreateWorkflowTransitionRequest(
          name = "Start",
          toStatusId = "sts_abc",
          fields = mapper.readTree("""{"title": {"editable": true}}"""),
        )

      request.fields.toJsonObject(mapper).keys shouldBe setOf("title")
    }

    "create issue type config request stores bindings" {
      val mapper = ObjectMapper()
      val request =
        CreateIssueTypeConfigRequest(
          scope = "tenant",
          projectId = null,
          issueTypeId = "typ_abc",
          workflowId = "wfl_abc",
          nameOverride = "Custom",
          iconOverride = "bug",
          colorOverride = "#fff",
          rank = 50,
          createFields = mapper.readTree("""{"title": {"editable": true}}"""),
          statuses =
            listOf(
              TypeConfigStatusRequest(
                statusId = "sts_abc",
                isInitial = true,
                isTerminal = false,
                rank = 1,
              )
            ),
          properties =
            listOf(
              TypeConfigPropertyRequest(
                propertyId = "fld_abc",
                validationOverride = mapper.readTree("""{"required": true}"""),
                rank = 2,
                displayConfig = mapper.readTree("""{"label": "Points"}"""),
              )
            ),
        )

      request.scope shouldBe "tenant"
      request.statuses.single().statusId shouldBe "sts_abc"
      request.properties?.single()?.propertyId shouldBe "fld_abc"
      request.createFields.toJsonObject(mapper).keys shouldBe setOf("title")
    }

    "comment request types store body text" {
      CreateWorkItemCommentRequest(body = "Created").body shouldBe "Created"
      UpdateWorkItemCommentRequest(body = "Updated").body shouldBe "Updated"
    }
  })
