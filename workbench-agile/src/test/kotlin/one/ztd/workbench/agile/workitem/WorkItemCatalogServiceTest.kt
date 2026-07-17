package one.ztd.workbench.agile.workitem

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import one.ztd.workbench.agile.workitem.model.CreateIssueStatusCommand
import one.ztd.workbench.agile.workitem.model.CreateIssueTypeCommand
import one.ztd.workbench.agile.workitem.model.CreatePropertyDefinitionCommand
import one.ztd.workbench.agile.workitem.model.CreateWorkflowCommand
import one.ztd.workbench.agile.workitem.model.IssueStatusRecord
import one.ztd.workbench.agile.workitem.model.IssueTypeRecord
import one.ztd.workbench.agile.workitem.model.PropertyDefinitionRecord
import one.ztd.workbench.agile.workitem.model.WorkItemConfigScope
import one.ztd.workbench.agile.workitem.model.WorkItemPropertyDataType
import one.ztd.workbench.agile.workitem.model.WorkItemStatusGroup
import one.ztd.workbench.agile.workitem.model.WorkflowRecord
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.ids.PublicId

class WorkItemCatalogServiceTest :
  StringSpec({
    val tenantId = UUID.randomUUID()
    val actorId = UUID.randomUUID()

    "createIssueType rejects project scope without project id" {
      val repository = mockk<WorkItemCatalogRepository>(relaxed = true)
      val service = WorkItemCatalogService(repository)

      shouldThrow<InvalidRequestException> {
        service.createIssueType(
          CreateIssueTypeCommand(
            tenantId = tenantId,
            scope = WorkItemConfigScope.PROJECT,
            projectId = null,
            code = "bug",
            name = "Bug",
          )
        )
      }
    }

    "listStatuses delegates to repository" {
      val repository = mockk<WorkItemCatalogRepository>()
      val status =
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
          createdAt = OffsetDateTime.now(ZoneOffset.UTC),
          updatedAt = OffsetDateTime.now(ZoneOffset.UTC),
        )
      coEvery { repository.listStatuses(tenantId) } returns listOf(status)
      val service = WorkItemCatalogService(repository)

      service.listStatuses(tenantId).single().code shouldBe "todo"
      coVerify { repository.listStatuses(tenantId) }
    }

    "createStatus and deactivateStatus delegate to repository" {
      val repository = mockk<WorkItemCatalogRepository>()
      val status =
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
          createdAt = OffsetDateTime.now(ZoneOffset.UTC),
          updatedAt = OffsetDateTime.now(ZoneOffset.UTC),
        )
      val command =
        CreateIssueStatusCommand(
          tenantId = tenantId,
          code = "todo",
          name = "To Do",
          statusGroup = WorkItemStatusGroup.TODO,
        )
      coEvery { repository.createStatus(command) } returns status
      coEvery { repository.deactivateStatus(tenantId, "todo", actorId) } returns
        status.copy(isActive = false)
      val service = WorkItemCatalogService(repository)

      service.createStatus(command).code shouldBe "todo"
      service.deactivateStatus(tenantId, "todo", actorId).isActive shouldBe false
      coVerify { repository.createStatus(command) }
      coVerify { repository.deactivateStatus(tenantId, "todo", actorId) }
    }

    "listIssueTypes delegates to repository" {
      val repository = mockk<WorkItemCatalogRepository>()
      val issueType =
        IssueTypeRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("typ"),
          tenantId = tenantId,
          scope = WorkItemConfigScope.TENANT,
          projectId = null,
          code = "task",
          name = "Task",
          description = null,
          icon = null,
          color = null,
          rank = 100,
          isActive = true,
          createdAt = OffsetDateTime.now(ZoneOffset.UTC),
          updatedAt = OffsetDateTime.now(ZoneOffset.UTC),
        )
      coEvery { repository.listIssueTypes(tenantId, null) } returns listOf(issueType)
      val service = WorkItemCatalogService(repository)

      service.listIssueTypes(tenantId).single().code shouldBe "task"
      coVerify { repository.listIssueTypes(tenantId, null) }
    }

    "createIssueType rejects tenant scope with project id" {
      val repository = mockk<WorkItemCatalogRepository>(relaxed = true)
      val service = WorkItemCatalogService(repository)

      shouldThrow<InvalidRequestException> {
        service.createIssueType(
          CreateIssueTypeCommand(
            tenantId = tenantId,
            scope = WorkItemConfigScope.TENANT,
            projectId = UUID.randomUUID(),
            code = "bug",
            name = "Bug",
          )
        )
      }
    }

    "deactivateIssueType delegates to repository" {
      val repository = mockk<WorkItemCatalogRepository>()
      val issueType =
        IssueTypeRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("typ"),
          tenantId = tenantId,
          scope = WorkItemConfigScope.TENANT,
          projectId = null,
          code = "task",
          name = "Task",
          description = null,
          icon = null,
          color = null,
          rank = 100,
          isActive = false,
          createdAt = OffsetDateTime.now(ZoneOffset.UTC),
          updatedAt = OffsetDateTime.now(ZoneOffset.UTC),
        )
      coEvery { repository.deactivateIssueType(tenantId, "task", actorId, null) } returns issueType
      val service = WorkItemCatalogService(repository)

      service.deactivateIssueType(tenantId, "task", actorId).isActive shouldBe false
    }

    "createProperty and listProperties delegate to repository" {
      val repository = mockk<WorkItemCatalogRepository>()
      val property =
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
          validationSchema = kotlinx.serialization.json.JsonObject(emptyMap()),
          searchConfig = kotlinx.serialization.json.JsonObject(emptyMap()),
          isActive = true,
          createdAt = OffsetDateTime.now(ZoneOffset.UTC),
          updatedAt = OffsetDateTime.now(ZoneOffset.UTC),
        )
      val command =
        CreatePropertyDefinitionCommand(
          tenantId = tenantId,
          code = "points",
          name = "Points",
          dataType = WorkItemPropertyDataType.NUMBER,
        )
      coEvery { repository.createProperty(command) } returns property
      coEvery { repository.listProperties(tenantId) } returns listOf(property)
      val service = WorkItemCatalogService(repository)

      service.createProperty(command).code shouldBe "points"
      service.listProperties(tenantId).single().name shouldBe "Points"
      coEvery { repository.deactivateProperty(tenantId, "points", actorId) } returns
        property.copy(isActive = false)
      service.deactivateProperty(tenantId, "points", actorId).isActive shouldBe false
    }
  })

class WorkflowConfigurationServiceTest :
  StringSpec({
    val tenantId = UUID.randomUUID()
    val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
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
        createdAt = OffsetDateTime.now(ZoneOffset.UTC),
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC),
      )

    "publishWorkflow throws when workflow is missing" {
      val repository = mockk<WorkflowConfigurationRepository>()
      val catalog = mockk<WorkItemCatalogRepository>(relaxed = true)
      val configs = mockk<IssueTypeConfigRepository>(relaxed = true)
      coEvery { repository.findWorkflow(tenantId, "missing") } returns null
      val service = WorkflowConfigurationService(repository, catalog, configs, clock)

      shouldThrow<ResourceNotFoundException> { service.publishWorkflow(tenantId, "missing") }
    }

    "listWorkflows delegates to repository" {
      val repository = mockk<WorkflowConfigurationRepository>()
      val catalog = mockk<WorkItemCatalogRepository>(relaxed = true)
      val configs = mockk<IssueTypeConfigRepository>(relaxed = true)
      coEvery { repository.listWorkflows(tenantId) } returns listOf(workflow)
      val service = WorkflowConfigurationService(repository, catalog, configs, clock)

      service.listWorkflows(tenantId).single().code shouldBe "default"
    }

    "createWorkflow delegates to repository" {
      val repository = mockk<WorkflowConfigurationRepository>()
      val catalog = mockk<WorkItemCatalogRepository>(relaxed = true)
      val configs = mockk<IssueTypeConfigRepository>(relaxed = true)
      val command =
        CreateWorkflowCommand(
          tenantId = tenantId,
          code = "support",
          name = "Support",
          createdBy = UUID.randomUUID(),
        )
      coEvery { repository.createWorkflow(command) } returns workflow.copy(code = "support")
      val service = WorkflowConfigurationService(repository, catalog, configs, clock)

      service.createWorkflow(command).code shouldBe "support"
    }

    "publishWorkflow publishes existing workflow" {
      val repository = mockk<WorkflowConfigurationRepository>()
      val catalog = mockk<WorkItemCatalogRepository>(relaxed = true)
      val configs = mockk<IssueTypeConfigRepository>(relaxed = true)
      coEvery { repository.findWorkflow(tenantId, "default") } returns workflow
      coEvery { repository.publishWorkflow(tenantId, workflow.id, any()) } returns
        workflow.copy(publishedAt = OffsetDateTime.now(ZoneOffset.UTC))
      val service = WorkflowConfigurationService(repository, catalog, configs, clock)

      service.publishWorkflow(tenantId, "default").publishedAt.shouldNotBeNull()
    }

    "deactivateWorkflow delegates to repository" {
      val repository = mockk<WorkflowConfigurationRepository>()
      val catalog = mockk<WorkItemCatalogRepository>(relaxed = true)
      val configs = mockk<IssueTypeConfigRepository>(relaxed = true)
      val actorId = UUID.randomUUID()
      coEvery { repository.deactivateWorkflow(tenantId, "default", actorId) } returns
        workflow.copy(isActive = false)
      val service = WorkflowConfigurationService(repository, catalog, configs, clock)

      service.deactivateWorkflow(tenantId, "default", actorId).isActive shouldBe false
    }

    "listTransitions throws when workflow is missing" {
      val repository = mockk<WorkflowConfigurationRepository>()
      val catalog = mockk<WorkItemCatalogRepository>(relaxed = true)
      val configs = mockk<IssueTypeConfigRepository>(relaxed = true)
      coEvery { repository.findWorkflow(tenantId, "missing") } returns null
      val service = WorkflowConfigurationService(repository, catalog, configs, clock)

      shouldThrow<ResourceNotFoundException> { service.listTransitions(tenantId, "missing") }
    }
  })
