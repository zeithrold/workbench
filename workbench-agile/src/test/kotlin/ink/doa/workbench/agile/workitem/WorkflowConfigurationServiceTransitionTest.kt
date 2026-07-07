package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.IssueTypeConfigRepository
import ink.doa.workbench.core.workitem.WorkItemCatalogRepository
import ink.doa.workbench.core.workitem.WorkflowConfigurationRepository
import ink.doa.workbench.core.workitem.model.CreateWorkflowTransitionCommand
import ink.doa.workbench.core.workitem.model.IssueStatusRecord
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.IssueTypeConfigRecord
import ink.doa.workbench.core.workitem.model.IssueTypeConfigStatusRecord
import ink.doa.workbench.core.workitem.model.WorkItemConfigScope
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
import ink.doa.workbench.core.workitem.model.WorkflowRecord
import ink.doa.workbench.core.workitem.model.WorkflowTransitionRecord
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class WorkflowConfigurationServiceTransitionTest :
  StringSpec({
    val tenantId = UUID.randomUUID()
    val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
    val now = OffsetDateTime.now(ZoneOffset.UTC)
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
        publishedAt = null,
        createdBy = UUID.randomUUID(),
        createdAt = now,
        updatedAt = now,
      )
    val todoStatus =
      IssueStatusRecord(
        id = UUID.randomUUID(),
        apiId = PublicId.new("sts"),
        tenantId = tenantId,
        code = "todo",
        name = "To Do",
        statusGroup = WorkItemStatusGroup.TODO,
        rank = 10,
        color = null,
        isTerminal = false,
        isActive = true,
        createdAt = now,
        updatedAt = now,
      )
    val doneStatus =
      todoStatus.copy(
        id = UUID.randomUUID(),
        apiId = PublicId.new("sts"),
        code = "done",
        name = "Done",
        statusGroup = WorkItemStatusGroup.DONE,
        isTerminal = true,
      )
    val transitionFields =
      Json.parseToJsonElement(
          """
          {
            "version": 1,
            "resource": "work_item",
            "target": "transition",
            "fields": {
              "title": { "participation": "optional" }
            }
          }
          """
            .trimIndent()
        )
        .jsonObject

    "createTransition validates statuses and delegates to repository" {
      val repository = mockk<WorkflowConfigurationRepository>()
      val catalog = mockk<WorkItemCatalogRepository>()
      val configs = mockk<IssueTypeConfigRepository>()
      val command =
        CreateWorkflowTransitionCommand(
          tenantId = tenantId,
          workflowApiId = workflow.apiId.value,
          name = "Complete",
          fromStatusApiId = todoStatus.apiId.value,
          toStatusApiId = doneStatus.apiId.value,
          fields = transitionFields,
        )
      val created =
        WorkflowTransitionRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("trn"),
          tenantId = tenantId,
          workflowId = workflow.id,
          name = "Complete",
          fromStatusId = todoStatus.id,
          fromStatusApiId = todoStatus.apiId,
          toStatusId = doneStatus.id,
          toStatusApiId = doneStatus.apiId,
          rank = 100,
          permissionCondition = JsonObject(emptyMap()),
          preconditionAst = JsonObject(emptyMap()),
          fields = command.fields,
          isActive = true,
          createdAt = now,
          updatedAt = now,
        )

      coEvery { repository.findWorkflow(tenantId, workflow.apiId.value) } returns workflow
      coEvery { catalog.findStatus(tenantId, todoStatus.apiId.value) } returns todoStatus
      coEvery { catalog.findStatus(tenantId, doneStatus.apiId.value) } returns doneStatus
      coEvery { configs.listConfigs(tenantId) } returns emptyList()
      coEvery { repository.createTransition(command) } returns created

      val service = WorkflowConfigurationService(repository, catalog, configs, clock)

      service.createTransition(command).name shouldBe "Complete"
      coVerify { repository.createTransition(command) }
    }

    "createTransition rejects published workflow updates" {
      val repository = mockk<WorkflowConfigurationRepository>()
      val catalog = mockk<WorkItemCatalogRepository>(relaxed = true)
      val configs = mockk<IssueTypeConfigRepository>(relaxed = true)
      val published = workflow.copy(publishedAt = now)
      val command =
        CreateWorkflowTransitionCommand(
          tenantId = tenantId,
          workflowApiId = published.apiId.value,
          name = "Complete",
          fromStatusApiId = null,
          toStatusApiId = doneStatus.apiId.value,
          fields = JsonObject(emptyMap()),
        )

      coEvery { repository.findWorkflow(tenantId, published.apiId.value) } returns published
      coEvery { catalog.findStatus(tenantId, doneStatus.apiId.value) } returns doneStatus

      shouldThrow<InvalidRequestException> {
        WorkflowConfigurationService(repository, catalog, configs, clock).createTransition(command)
      }
    }

    "createTransition rejects transition outside active type config statuses" {
      val repository = mockk<WorkflowConfigurationRepository>()
      val catalog = mockk<WorkItemCatalogRepository>()
      val configs = mockk<IssueTypeConfigRepository>()
      val configDetails = activeConfig(workflow, todoStatus)
      val command =
        CreateWorkflowTransitionCommand(
          tenantId = tenantId,
          workflowApiId = workflow.apiId.value,
          name = "Complete",
          fromStatusApiId = todoStatus.apiId.value,
          toStatusApiId = doneStatus.apiId.value,
          fields = transitionFields,
        )

      coEvery { repository.findWorkflow(tenantId, workflow.apiId.value) } returns workflow
      coEvery { catalog.findStatus(tenantId, todoStatus.apiId.value) } returns todoStatus
      coEvery { catalog.findStatus(tenantId, doneStatus.apiId.value) } returns doneStatus
      coEvery { configs.listConfigs(tenantId) } returns listOf(configDetails)

      shouldThrow<InvalidRequestException> {
        WorkflowConfigurationService(repository, catalog, configs, clock).createTransition(command)
      }
    }

    "deactivateTransition requires unpublished workflow and existing transition" {
      val repository = mockk<WorkflowConfigurationRepository>()
      val catalog = mockk<WorkItemCatalogRepository>(relaxed = true)
      val configs = mockk<IssueTypeConfigRepository>(relaxed = true)
      val transition =
        WorkflowTransitionRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("trn"),
          tenantId = tenantId,
          workflowId = workflow.id,
          name = "Complete",
          fromStatusId = null,
          fromStatusApiId = null,
          toStatusId = doneStatus.id,
          toStatusApiId = doneStatus.apiId,
          rank = 100,
          permissionCondition = JsonObject(emptyMap()),
          preconditionAst = JsonObject(emptyMap()),
          fields = JsonObject(emptyMap()),
          isActive = true,
          createdAt = now,
          updatedAt = now,
        )

      coEvery { repository.findWorkflow(tenantId, workflow.apiId.value) } returns workflow
      coEvery { repository.findTransition(tenantId, transition.apiId.value) } returns transition
      coEvery { repository.deactivateTransition(tenantId, transition.apiId.value) } returns
        transition.copy(isActive = false)

      val result =
        WorkflowConfigurationService(repository, catalog, configs, clock)
          .deactivateTransition(tenantId, workflow.apiId.value, transition.apiId.value)

      result.isActive shouldBe false
    }

    "deactivateTransition throws when transition is missing" {
      val repository = mockk<WorkflowConfigurationRepository>()
      val catalog = mockk<WorkItemCatalogRepository>(relaxed = true)
      val configs = mockk<IssueTypeConfigRepository>(relaxed = true)

      coEvery { repository.findWorkflow(tenantId, workflow.apiId.value) } returns workflow
      coEvery { repository.findTransition(tenantId, "missing") } returns null

      shouldThrow<ResourceNotFoundException> {
        WorkflowConfigurationService(repository, catalog, configs, clock)
          .deactivateTransition(tenantId, workflow.apiId.value, "missing")
      }
    }

    "deactivateTransition throws when transition belongs to another workflow" {
      val repository = mockk<WorkflowConfigurationRepository>()
      val catalog = mockk<WorkItemCatalogRepository>(relaxed = true)
      val configs = mockk<IssueTypeConfigRepository>(relaxed = true)
      val otherWorkflowId = UUID.randomUUID()
      val transition =
        WorkflowTransitionRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("trn"),
          tenantId = tenantId,
          workflowId = otherWorkflowId,
          name = "Complete",
          fromStatusId = null,
          fromStatusApiId = null,
          toStatusId = doneStatus.id,
          toStatusApiId = doneStatus.apiId,
          rank = 100,
          permissionCondition = JsonObject(emptyMap()),
          preconditionAst = JsonObject(emptyMap()),
          fields = JsonObject(emptyMap()),
          isActive = true,
          createdAt = now,
          updatedAt = now,
        )

      coEvery { repository.findWorkflow(tenantId, workflow.apiId.value) } returns workflow
      coEvery { repository.findTransition(tenantId, transition.apiId.value) } returns transition

      shouldThrow<ResourceNotFoundException> {
          WorkflowConfigurationService(repository, catalog, configs, clock)
            .deactivateTransition(tenantId, workflow.apiId.value, transition.apiId.value)
        }
        .errorCode shouldBe WorkbenchErrorCode.RESOURCE_WORKFLOW_TRANSITION_NOT_FOUND
    }

    "createTransition canonicalizes conditions before persisting" {
      val repository = mockk<WorkflowConfigurationRepository>()
      val catalog = mockk<WorkItemCatalogRepository>()
      val configs = mockk<IssueTypeConfigRepository>()
      val rawCondition =
        Json.parseToJsonElement(
            """
            {
              "field": "statusGroup",
              "op": "==",
              "value": "todo"
            }
            """
              .trimIndent()
          )
          .jsonObject
      val command =
        CreateWorkflowTransitionCommand(
          tenantId = tenantId,
          workflowApiId = workflow.apiId.value,
          name = "Complete",
          fromStatusApiId = todoStatus.apiId.value,
          toStatusApiId = doneStatus.apiId.value,
          permissionCondition = rawCondition,
          preconditionAst = rawCondition,
          fields = transitionFields,
        )
      val commandSlot = slot<CreateWorkflowTransitionCommand>()
      val created =
        WorkflowTransitionRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("trn"),
          tenantId = tenantId,
          workflowId = workflow.id,
          name = "Complete",
          fromStatusId = todoStatus.id,
          fromStatusApiId = todoStatus.apiId,
          toStatusId = doneStatus.id,
          toStatusApiId = doneStatus.apiId,
          rank = 100,
          permissionCondition = JsonObject(emptyMap()),
          preconditionAst = JsonObject(emptyMap()),
          fields = command.fields,
          isActive = true,
          createdAt = now,
          updatedAt = now,
        )

      coEvery { repository.findWorkflow(tenantId, workflow.apiId.value) } returns workflow
      coEvery { catalog.findStatus(tenantId, todoStatus.apiId.value) } returns todoStatus
      coEvery { catalog.findStatus(tenantId, doneStatus.apiId.value) } returns doneStatus
      coEvery { configs.listConfigs(tenantId) } returns emptyList()
      coEvery { repository.createTransition(capture(commandSlot)) } returns created

      WorkflowConfigurationService(repository, catalog, configs, clock).createTransition(command)

      commandSlot.captured.permissionCondition shouldBe JsonObject(emptyMap())
      commandSlot.captured.preconditionAst["op"]!!.jsonPrimitive.content shouldBe "eq"
      commandSlot.captured.preconditionAst["field"]!!.jsonPrimitive.content shouldBe
        "issue.statusGroup"
      coVerify { repository.createTransition(any()) }
    }

    "createTransition rejects unsupported condition operators before persisting" {
      val repository = mockk<WorkflowConfigurationRepository>()
      val catalog = mockk<WorkItemCatalogRepository>()
      val configs = mockk<IssueTypeConfigRepository>()
      val unsupportedCondition =
        Json.parseToJsonElement(
            """
            {
              "field": "issue.statusGroup",
              "op": "between",
              "value": [1, 2]
            }
            """
              .trimIndent()
          )
          .jsonObject
      val command =
        CreateWorkflowTransitionCommand(
          tenantId = tenantId,
          workflowApiId = workflow.apiId.value,
          name = "Complete",
          fromStatusApiId = todoStatus.apiId.value,
          toStatusApiId = doneStatus.apiId.value,
          preconditionAst = unsupportedCondition,
          fields = transitionFields,
        )

      coEvery { repository.findWorkflow(tenantId, workflow.apiId.value) } returns workflow
      coEvery { catalog.findStatus(tenantId, todoStatus.apiId.value) } returns todoStatus
      coEvery { catalog.findStatus(tenantId, doneStatus.apiId.value) } returns doneStatus
      coEvery { configs.listConfigs(tenantId) } returns emptyList()

      shouldThrow<InvalidRequestException> {
        WorkflowConfigurationService(repository, catalog, configs, clock).createTransition(command)
      }

      coVerify(exactly = 0) { repository.createTransition(any()) }
    }

    "createTransition accepts transition when active config includes both statuses" {
      val repository = mockk<WorkflowConfigurationRepository>()
      val catalog = mockk<WorkItemCatalogRepository>()
      val configs = mockk<IssueTypeConfigRepository>()
      val configDetails = activeConfigWithStatuses(workflow, todoStatus, doneStatus)
      val command =
        CreateWorkflowTransitionCommand(
          tenantId = tenantId,
          workflowApiId = workflow.apiId.value,
          name = "Complete",
          fromStatusApiId = todoStatus.apiId.value,
          toStatusApiId = doneStatus.apiId.value,
          fields = transitionFields,
        )
      val created =
        WorkflowTransitionRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("trn"),
          tenantId = tenantId,
          workflowId = workflow.id,
          name = "Complete",
          fromStatusId = todoStatus.id,
          fromStatusApiId = todoStatus.apiId,
          toStatusId = doneStatus.id,
          toStatusApiId = doneStatus.apiId,
          rank = 100,
          permissionCondition = JsonObject(emptyMap()),
          preconditionAst = JsonObject(emptyMap()),
          fields = command.fields,
          isActive = true,
          createdAt = now,
          updatedAt = now,
        )

      coEvery { repository.findWorkflow(tenantId, workflow.apiId.value) } returns workflow
      coEvery { catalog.findStatus(tenantId, todoStatus.apiId.value) } returns todoStatus
      coEvery { catalog.findStatus(tenantId, doneStatus.apiId.value) } returns doneStatus
      coEvery { configs.listConfigs(tenantId) } returns listOf(configDetails)
      coEvery { repository.createTransition(command) } returns created

      WorkflowConfigurationService(repository, catalog, configs, clock)
        .createTransition(command)
        .name shouldBe "Complete"
    }
  })

private fun activeConfig(
  workflow: WorkflowRecord,
  status: IssueStatusRecord,
): IssueTypeConfigDetails = activeConfigWithStatuses(workflow, status)

private fun activeConfigWithStatuses(
  workflow: WorkflowRecord,
  vararg statuses: IssueStatusRecord,
): IssueTypeConfigDetails {
  val now = OffsetDateTime.now(ZoneOffset.UTC)
  val configId = UUID.randomUUID()
  return IssueTypeConfigDetails(
    config =
      IssueTypeConfigRecord(
        id = configId,
        apiId = PublicId.new("itc"),
        tenantId = workflow.tenantId,
        scope = WorkItemConfigScope.TENANT,
        projectId = null,
        issueTypeId = UUID.randomUUID(),
        issueTypeApiId = PublicId.new("typ"),
        workflowId = workflow.id,
        workflowApiId = workflow.apiId,
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
    statuses =
      statuses.mapIndexed { index, status ->
        IssueTypeConfigStatusRecord(
          id = UUID.randomUUID(),
          tenantId = workflow.tenantId,
          issueTypeConfigId = configId,
          statusId = status.id,
          statusApiId = status.apiId,
          code = status.code,
          name = status.name,
          statusGroup = status.statusGroup,
          isInitial = index == 0,
          isTerminal = status.isTerminal,
          rank = index + 1,
        )
      },
    properties = emptyList(),
  )
}
