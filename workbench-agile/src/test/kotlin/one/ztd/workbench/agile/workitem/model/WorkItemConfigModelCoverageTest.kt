package one.ztd.workbench.agile.workitem.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import one.ztd.workbench.kernel.common.ids.PublicId

class WorkItemConfigModelCoverageTest :
  StringSpec({
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()

    "catalog and workflow records expose metadata" {
      val status =
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
      val property =
        PropertyDefinitionRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("fld"),
          tenantId = tenantId,
          code = "severity",
          name = "Severity",
          description = null,
          dataType = WorkItemPropertyDataType.SINGLE_SELECT,
          isSystem = false,
          isArray = false,
          validationSchema = JsonObject(emptyMap()),
          searchConfig = JsonObject(emptyMap()),
          isActive = true,
          createdAt = now,
          updatedAt = now,
        )
      val issueType =
        IssueTypeRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("typ"),
          tenantId = tenantId,
          projectId = projectId,
          scope = WorkItemConfigScope.PROJECT,
          code = "bug",
          name = "Bug",
          description = null,
          icon = null,
          color = null,
          rank = 1,
          isActive = true,
          createdAt = now,
          updatedAt = now,
        )
      val workflow =
        WorkflowRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("wfl"),
          tenantId = tenantId,
          code = "default",
          name = "Default",
          description = null,
          version = 1,
          isActive = true,
          publishedAt = now,
          createdBy = UUID.randomUUID(),
          createdAt = now,
          updatedAt = now,
        )
      val transition =
        WorkflowTransitionRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("trn"),
          tenantId = tenantId,
          workflowId = workflow.id,
          name = "Start",
          fromStatusId = null,
          fromStatusApiId = null,
          toStatusId = status.id,
          toStatusApiId = status.apiId,
          rank = 1,
          preconditionAst = JsonObject(emptyMap()),
          fields = JsonObject(emptyMap()),
          isActive = true,
          createdAt = now,
          updatedAt = now,
        )

      status.code shouldBe "todo"
      property.code shouldBe "severity"
      issueType.code shouldBe "bug"
      workflow.code shouldBe "default"
      transition.name shouldBe "Start"
    }

    "issue type config records expose bindings" {
      val configId = UUID.randomUUID()
      val config =
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
          createdBy = UUID.randomUUID(),
          createdAt = now,
          updatedAt = now,
          createFields = JsonObject(emptyMap()),
        )
      val statusBinding =
        IssueTypeConfigStatusRecord(
          id = UUID.randomUUID(),
          tenantId = tenantId,
          issueTypeConfigId = configId,
          statusId = UUID.randomUUID(),
          statusApiId = PublicId.new("sts"),
          code = "todo",
          name = "To Do",
          statusGroup = WorkItemStatusGroup.TODO,
          isInitial = true,
          isTerminal = false,
          rank = 1,
        )
      val propertyBinding =
        IssueTypeConfigPropertyRecord(
          id = UUID.randomUUID(),
          tenantId = tenantId,
          issueTypeConfigId = configId,
          propertyId = UUID.randomUUID(),
          propertyApiId = PublicId.new("fld"),
          code = "points",
          name = "Points",
          dataType = WorkItemPropertyDataType.NUMBER,
          validationOverride = JsonObject(emptyMap()),
          rank = 1,
          displayConfig = JsonObject(emptyMap()),
        )

      IssueTypeConfigDetails(config, listOf(statusBinding), listOf(propertyBinding))
        .statuses
        .single()
        .isInitial shouldBe true
      propertyBinding.code shouldBe "points"
    }

    "create issue type config command stores bindings" {
      CreateIssueTypeConfigCommand(
          tenantId = tenantId,
          scope = WorkItemConfigScope.TENANT,
          projectId = null,
          issueTypeApiId = "typ_abc",
          workflowApiId = "wfl_abc",
          createFields = JsonObject(emptyMap()),
          statuses = listOf(IssueTypeConfigStatusInput("sts_abc", isInitial = true)),
          properties = listOf(IssueTypeConfigPropertyInput("fld_abc")),
        )
        .issueTypeApiId shouldBe "typ_abc"
    }
  })
