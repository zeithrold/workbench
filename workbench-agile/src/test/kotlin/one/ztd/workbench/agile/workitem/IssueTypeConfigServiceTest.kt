package one.ztd.workbench.agile.workitem

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import one.ztd.workbench.agile.workitem.model.CreateIssueTypeConfigCommand
import one.ztd.workbench.agile.workitem.model.EffectiveIssueTypeConfig
import one.ztd.workbench.agile.workitem.model.IssueStatusRecord
import one.ztd.workbench.agile.workitem.model.IssueTypeConfigDetails
import one.ztd.workbench.agile.workitem.model.IssueTypeConfigPropertyInput
import one.ztd.workbench.agile.workitem.model.IssueTypeConfigRecord
import one.ztd.workbench.agile.workitem.model.IssueTypeConfigStatusInput
import one.ztd.workbench.agile.workitem.model.IssueTypeConfigStatusRecord
import one.ztd.workbench.agile.workitem.model.IssueTypeRecord
import one.ztd.workbench.agile.workitem.model.PropertyDefinitionRecord
import one.ztd.workbench.agile.workitem.model.WorkItemConfigScope
import one.ztd.workbench.agile.workitem.model.WorkItemPropertyDataType
import one.ztd.workbench.agile.workitem.model.WorkItemStatusGroup
import one.ztd.workbench.agile.workitem.model.WorkflowRecord
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.ids.PublicId

class IssueTypeConfigServiceTest :
  StringSpec({
    val createFields =
      Json.parseToJsonElement(
        """
        {
          "version": 1,
          "resource": "work_item",
          "target": "create",
          "fields": {
            "title": { "participation": "required" }
          }
        }
        """
          .trimIndent()
      )

    "requires exactly one initial status" {
      val configs = mockk<IssueTypeConfigRepository>(relaxed = true)
      val catalog = mockk<WorkItemCatalogRepository>(relaxed = true)
      val workflows = mockk<WorkflowConfigurationRepository>(relaxed = true)
      val command =
        CreateIssueTypeConfigCommand(
          tenantId = UUID.randomUUID(),
          scope = WorkItemConfigScope.TENANT,
          projectId = null,
          issueTypeApiId = "typ_01H00000000000000000000000",
          workflowApiId = "wfl_01H00000000000000000000000",
          createFields = createFields.jsonObject,
          statuses =
            listOf(
              IssueTypeConfigStatusInput("sts_01H00000000000000000000000"),
              IssueTypeConfigStatusInput("sts_01H00000000000000000000001"),
            ),
        )

      shouldThrow<InvalidRequestException> {
        IssueTypeConfigService(configs, catalog, workflows).create(command)
      }

      coVerify(exactly = 0) { configs.createConfig(any()) }
    }

    "rejects tenant scoped config with project id" {
      val configs = mockk<IssueTypeConfigRepository>(relaxed = true)
      val catalog = mockk<WorkItemCatalogRepository>(relaxed = true)
      val workflows = mockk<WorkflowConfigurationRepository>(relaxed = true)
      val command =
        CreateIssueTypeConfigCommand(
          tenantId = UUID.randomUUID(),
          scope = WorkItemConfigScope.TENANT,
          projectId = UUID.randomUUID(),
          issueTypeApiId = "typ_01H00000000000000000000000",
          workflowApiId = "wfl_01H00000000000000000000000",
          createFields = createFields.jsonObject,
          statuses =
            listOf(IssueTypeConfigStatusInput("sts_01H00000000000000000000000", isInitial = true)),
        )

      shouldThrow<InvalidRequestException> {
        IssueTypeConfigService(configs, catalog, workflows).create(command)
      }

      coVerify(exactly = 0) { configs.createConfig(any()) }
    }

    "list delegates to repository" {
      val tenantId = UUID.randomUUID()
      val configs = mockk<IssueTypeConfigRepository>()
      val catalog = mockk<WorkItemCatalogRepository>(relaxed = true)
      val workflows = mockk<WorkflowConfigurationRepository>(relaxed = true)
      val details = sampleConfigDetails(tenantId)
      coEvery { configs.listConfigs(tenantId, null) } returns listOf(details)
      val service = IssueTypeConfigService(configs, catalog, workflows)

      service.list(tenantId).single().config.apiId shouldBe details.config.apiId
    }

    "resolveEffective throws when config is missing" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val configs = mockk<IssueTypeConfigRepository>()
      val catalog = mockk<WorkItemCatalogRepository>(relaxed = true)
      val workflows = mockk<WorkflowConfigurationRepository>(relaxed = true)
      coEvery { configs.resolveEffective(tenantId, projectId, "task") } returns null
      val service = IssueTypeConfigService(configs, catalog, workflows)

      shouldThrow<ResourceNotFoundException> {
        service.resolveEffective(tenantId, projectId, "task")
      }
    }

    "create validates bindings and persists config" {
      val tenantId = UUID.randomUUID()
      val configs = mockk<IssueTypeConfigRepository>()
      val catalog = mockk<WorkItemCatalogRepository>()
      val workflows = mockk<WorkflowConfigurationRepository>()
      val now = OffsetDateTime.now(ZoneOffset.UTC)
      val issueType =
        IssueTypeRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("typ"),
          tenantId = tenantId,
          projectId = null,
          scope = WorkItemConfigScope.TENANT,
          code = "task",
          name = "Task",
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
          publishedAt = null,
          createdBy = UUID.randomUUID(),
          createdAt = now,
          updatedAt = now,
        )
      val status =
        IssueStatusRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("sts"),
          tenantId = tenantId,
          code = "todo",
          name = "To Do",
          statusGroup = WorkItemStatusGroup.TODO,
          rank = 1,
          color = null,
          isTerminal = false,
          isActive = true,
          createdAt = now,
          updatedAt = now,
        )
      val command =
        CreateIssueTypeConfigCommand(
          tenantId = tenantId,
          scope = WorkItemConfigScope.TENANT,
          projectId = null,
          issueTypeApiId = issueType.apiId.value,
          workflowApiId = workflow.apiId.value,
          createFields = createFields.jsonObject,
          statuses = listOf(IssueTypeConfigStatusInput(status.apiId.value, isInitial = true)),
        )
      val created = sampleConfigDetails(tenantId)

      coEvery { catalog.findIssueType(tenantId, issueType.apiId.value, null) } returns issueType
      coEvery { workflows.findWorkflow(tenantId, workflow.apiId.value) } returns workflow
      coEvery { catalog.findStatus(tenantId, status.apiId.value) } returns status
      coEvery { workflows.listTransitions(tenantId, workflow.id) } returns emptyList()
      coEvery { configs.createConfig(command) } returns created

      IssueTypeConfigService(configs, catalog, workflows).create(command) shouldBe created
      coVerify { configs.createConfig(command) }
    }

    "resolveEffective returns effective config" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val configs = mockk<IssueTypeConfigRepository>()
      val effective =
        EffectiveIssueTypeConfig(sampleConfigDetails(tenantId), WorkItemConfigScope.TENANT)
      coEvery { configs.resolveEffective(tenantId, projectId, "task") } returns effective
      val service = IssueTypeConfigService(configs, mockk(relaxed = true), mockk(relaxed = true))

      service.resolveEffective(tenantId, projectId, "task") shouldBe effective
    }

    "rejects duplicate statuses" {
      val configs = mockk<IssueTypeConfigRepository>(relaxed = true)
      val catalog = mockk<WorkItemCatalogRepository>(relaxed = true)
      val workflows = mockk<WorkflowConfigurationRepository>(relaxed = true)
      val statusApiId = "sts_01H00000000000000000000000"
      val command =
        CreateIssueTypeConfigCommand(
          tenantId = UUID.randomUUID(),
          scope = WorkItemConfigScope.TENANT,
          projectId = null,
          issueTypeApiId = "typ_01H00000000000000000000000",
          workflowApiId = "wfl_01H00000000000000000000000",
          createFields = createFields.jsonObject,
          statuses =
            listOf(
              IssueTypeConfigStatusInput(statusApiId, isInitial = true),
              IssueTypeConfigStatusInput(statusApiId),
            ),
        )

      shouldThrow<InvalidRequestException> {
        IssueTypeConfigService(configs, catalog, workflows).create(command)
      }

      coVerify(exactly = 0) { configs.createConfig(any()) }
    }

    "rejects project scoped config without project id" {
      val configs = mockk<IssueTypeConfigRepository>(relaxed = true)
      val catalog = mockk<WorkItemCatalogRepository>(relaxed = true)
      val workflows = mockk<WorkflowConfigurationRepository>(relaxed = true)
      val command =
        CreateIssueTypeConfigCommand(
          tenantId = UUID.randomUUID(),
          scope = WorkItemConfigScope.PROJECT,
          projectId = null,
          issueTypeApiId = "typ_01H00000000000000000000000",
          workflowApiId = "wfl_01H00000000000000000000000",
          createFields = createFields.jsonObject,
          statuses =
            listOf(IssueTypeConfigStatusInput("sts_01H00000000000000000000000", isInitial = true)),
        )

      shouldThrow<InvalidRequestException> {
        IssueTypeConfigService(configs, catalog, workflows).create(command)
      }

      coVerify(exactly = 0) { configs.createConfig(any()) }
    }

    "rejects workflow transitions that reference unavailable statuses" {
      val tenantId = UUID.randomUUID()
      val configs = mockk<IssueTypeConfigRepository>()
      val catalog = mockk<WorkItemCatalogRepository>()
      val workflows = mockk<WorkflowConfigurationRepository>()
      val now = OffsetDateTime.now(ZoneOffset.UTC)
      val issueType =
        IssueTypeRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("typ"),
          tenantId = tenantId,
          projectId = null,
          scope = WorkItemConfigScope.TENANT,
          code = "task",
          name = "Task",
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
          publishedAt = null,
          createdBy = UUID.randomUUID(),
          createdAt = now,
          updatedAt = now,
        )
      val status =
        IssueStatusRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("sts"),
          tenantId = tenantId,
          code = "todo",
          name = "To Do",
          statusGroup = WorkItemStatusGroup.TODO,
          rank = 1,
          color = null,
          isTerminal = false,
          isActive = true,
          createdAt = now,
          updatedAt = now,
        )
      val command =
        CreateIssueTypeConfigCommand(
          tenantId = tenantId,
          scope = WorkItemConfigScope.TENANT,
          projectId = null,
          issueTypeApiId = issueType.apiId.value,
          workflowApiId = workflow.apiId.value,
          createFields = createFields.jsonObject,
          statuses = listOf(IssueTypeConfigStatusInput(status.apiId.value, isInitial = true)),
        )
      val invalidTransition =
        one.ztd.workbench.agile.workitem.model.WorkflowTransitionRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("trn"),
          tenantId = tenantId,
          workflowId = workflow.id,
          name = "Done",
          fromStatusId = status.id,
          fromStatusApiId = status.apiId,
          toStatusId = UUID.randomUUID(),
          toStatusApiId = PublicId.new("sts"),
          rank = 100,
          preconditionAst = JsonObject(emptyMap()),
          fields = createFields.jsonObject,
          isActive = true,
          createdAt = now,
          updatedAt = now,
        )

      coEvery { catalog.findIssueType(tenantId, issueType.apiId.value, null) } returns issueType
      coEvery { workflows.findWorkflow(tenantId, workflow.apiId.value) } returns workflow
      coEvery { catalog.findStatus(tenantId, status.apiId.value) } returns status
      coEvery { workflows.listTransitions(tenantId, workflow.id) } returns listOf(invalidTransition)

      shouldThrow<InvalidRequestException> {
        IssueTypeConfigService(configs, catalog, workflows).create(command)
      }

      coVerify(exactly = 0) { configs.createConfig(any()) }
    }

    "rejects duplicate properties" {
      val configs = mockk<IssueTypeConfigRepository>(relaxed = true)
      val catalog = mockk<WorkItemCatalogRepository>(relaxed = true)
      val workflows = mockk<WorkflowConfigurationRepository>(relaxed = true)
      val propertyApiId = "fld_01H00000000000000000000000"
      val command =
        CreateIssueTypeConfigCommand(
          tenantId = UUID.randomUUID(),
          scope = WorkItemConfigScope.TENANT,
          projectId = null,
          issueTypeApiId = "typ_01H00000000000000000000000",
          workflowApiId = "wfl_01H00000000000000000000000",
          createFields = createFields.jsonObject,
          statuses =
            listOf(IssueTypeConfigStatusInput("sts_01H00000000000000000000000", isInitial = true)),
          properties =
            listOf(
              IssueTypeConfigPropertyInput(propertyApiId),
              IssueTypeConfigPropertyInput(propertyApiId),
            ),
        )

      shouldThrow<InvalidRequestException> {
        IssueTypeConfigService(configs, catalog, workflows).create(command)
      }

      coVerify(exactly = 0) { configs.createConfig(any()) }
    }

    "rejects missing issue type reference" {
      val tenantId = UUID.randomUUID()
      val configs = mockk<IssueTypeConfigRepository>(relaxed = true)
      val catalog = mockk<WorkItemCatalogRepository>()
      val workflows = mockk<WorkflowConfigurationRepository>(relaxed = true)
      val command =
        CreateIssueTypeConfigCommand(
          tenantId = tenantId,
          scope = WorkItemConfigScope.TENANT,
          projectId = null,
          issueTypeApiId = "typ_missing",
          workflowApiId = "wfl_01H00000000000000000000000",
          createFields = createFields.jsonObject,
          statuses =
            listOf(IssueTypeConfigStatusInput("sts_01H00000000000000000000000", isInitial = true)),
        )
      coEvery { catalog.findIssueType(tenantId, "typ_missing", null) } returns null

      shouldThrow<ResourceNotFoundException> {
        IssueTypeConfigService(configs, catalog, workflows).create(command)
      }
    }

    "rejects missing workflow reference" {
      val tenantId = UUID.randomUUID()
      val configs = mockk<IssueTypeConfigRepository>(relaxed = true)
      val catalog = mockk<WorkItemCatalogRepository>()
      val workflows = mockk<WorkflowConfigurationRepository>()
      val issueType = sampleIssueType(tenantId)
      val command =
        CreateIssueTypeConfigCommand(
          tenantId = tenantId,
          scope = WorkItemConfigScope.TENANT,
          projectId = null,
          issueTypeApiId = issueType.apiId.value,
          workflowApiId = "wfl_missing",
          createFields = createFields.jsonObject,
          statuses =
            listOf(IssueTypeConfigStatusInput("sts_01H00000000000000000000000", isInitial = true)),
        )
      coEvery { catalog.findIssueType(tenantId, issueType.apiId.value, null) } returns issueType
      coEvery { workflows.findWorkflow(tenantId, "wfl_missing") } returns null

      shouldThrow<ResourceNotFoundException> {
        IssueTypeConfigService(configs, catalog, workflows).create(command)
      }
    }

    "rejects missing status reference" {
      val tenantId = UUID.randomUUID()
      val configs = mockk<IssueTypeConfigRepository>(relaxed = true)
      val catalog = mockk<WorkItemCatalogRepository>()
      val workflows = mockk<WorkflowConfigurationRepository>()
      val issueType = sampleIssueType(tenantId)
      val workflow = sampleWorkflow(tenantId)
      val command =
        CreateIssueTypeConfigCommand(
          tenantId = tenantId,
          scope = WorkItemConfigScope.TENANT,
          projectId = null,
          issueTypeApiId = issueType.apiId.value,
          workflowApiId = workflow.apiId.value,
          createFields = createFields.jsonObject,
          statuses = listOf(IssueTypeConfigStatusInput("sts_missing", isInitial = true)),
        )
      coEvery { catalog.findIssueType(tenantId, issueType.apiId.value, null) } returns issueType
      coEvery { workflows.findWorkflow(tenantId, workflow.apiId.value) } returns workflow
      coEvery { catalog.findStatus(tenantId, "sts_missing") } returns null

      shouldThrow<ResourceNotFoundException> {
        IssueTypeConfigService(configs, catalog, workflows).create(command)
      }
    }

    "rejects missing property reference when properties are configured" {
      val tenantId = UUID.randomUUID()
      val configs = mockk<IssueTypeConfigRepository>(relaxed = true)
      val catalog = mockk<WorkItemCatalogRepository>()
      val workflows = mockk<WorkflowConfigurationRepository>(relaxed = true)
      val issueType = sampleIssueType(tenantId)
      val workflow = sampleWorkflow(tenantId)
      val status = sampleStatus(tenantId)
      val command =
        CreateIssueTypeConfigCommand(
          tenantId = tenantId,
          scope = WorkItemConfigScope.TENANT,
          projectId = null,
          issueTypeApiId = issueType.apiId.value,
          workflowApiId = workflow.apiId.value,
          createFields = createFields.jsonObject,
          statuses = listOf(IssueTypeConfigStatusInput(status.apiId.value, isInitial = true)),
          properties = listOf(IssueTypeConfigPropertyInput("fld_missing")),
        )
      coEvery { catalog.findIssueType(tenantId, issueType.apiId.value, null) } returns issueType
      coEvery { workflows.findWorkflow(tenantId, workflow.apiId.value) } returns workflow
      coEvery { catalog.findStatus(tenantId, status.apiId.value) } returns status
      coEvery { catalog.findProperty(tenantId, "fld_missing") } returns null

      shouldThrow<ResourceNotFoundException> {
        IssueTypeConfigService(configs, catalog, workflows).create(command)
      }
    }

    "list with projectId delegates to repository" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val configs = mockk<IssueTypeConfigRepository>()
      val catalog = mockk<WorkItemCatalogRepository>(relaxed = true)
      val workflows = mockk<WorkflowConfigurationRepository>(relaxed = true)
      val details = sampleConfigDetails(tenantId)
      coEvery { configs.listConfigs(tenantId, projectId) } returns listOf(details)
      val service = IssueTypeConfigService(configs, catalog, workflows)

      service.list(tenantId, projectId).single().config.apiId shouldBe details.config.apiId
      coVerify { configs.listConfigs(tenantId, projectId) }
    }

    "create with properties validates createFields against property config" {
      val tenantId = UUID.randomUUID()
      val configs = mockk<IssueTypeConfigRepository>()
      val catalog = mockk<WorkItemCatalogRepository>()
      val workflows = mockk<WorkflowConfigurationRepository>()
      val issueType = sampleIssueType(tenantId)
      val workflow = sampleWorkflow(tenantId)
      val status = sampleStatus(tenantId)
      val property = sampleProperty(tenantId)
      val createFieldsWithProperty =
        Json.parseToJsonElement(
          """
          {
            "version": 1,
            "resource": "work_item",
            "target": "create",
            "fields": {
              "title": { "participation": "required" },
              "property.severity": { "participation": "optional" }
            }
          }
          """
            .trimIndent()
        )
      val command =
        CreateIssueTypeConfigCommand(
          tenantId = tenantId,
          scope = WorkItemConfigScope.TENANT,
          projectId = null,
          issueTypeApiId = issueType.apiId.value,
          workflowApiId = workflow.apiId.value,
          createFields = createFieldsWithProperty.jsonObject,
          statuses = listOf(IssueTypeConfigStatusInput(status.apiId.value, isInitial = true)),
          properties = listOf(IssueTypeConfigPropertyInput(property.apiId.value)),
        )
      val created = sampleConfigDetails(tenantId)

      coEvery { catalog.findIssueType(tenantId, issueType.apiId.value, null) } returns issueType
      coEvery { workflows.findWorkflow(tenantId, workflow.apiId.value) } returns workflow
      coEvery { catalog.findStatus(tenantId, status.apiId.value) } returns status
      coEvery { catalog.findProperty(tenantId, property.apiId.value) } returns property
      coEvery { workflows.listTransitions(tenantId, workflow.id) } returns emptyList()
      coEvery { configs.createConfig(command) } returns created

      IssueTypeConfigService(configs, catalog, workflows).create(command) shouldBe created
      coVerify(exactly = 1) { catalog.findProperty(tenantId, property.apiId.value) }
    }

    "rejects invalid createFields template" {
      val tenantId = UUID.randomUUID()
      val configs = mockk<IssueTypeConfigRepository>(relaxed = true)
      val catalog = mockk<WorkItemCatalogRepository>()
      val workflows = mockk<WorkflowConfigurationRepository>()
      val issueType = sampleIssueType(tenantId)
      val workflow = sampleWorkflow(tenantId)
      val status = sampleStatus(tenantId)
      val invalidCreateFields =
        Json.parseToJsonElement(
          """
          {
            "version": 1,
            "resource": "work_item",
            "target": "transition",
            "fields": {
              "title": { "participation": "required" }
            }
          }
          """
            .trimIndent()
        )
      val command =
        CreateIssueTypeConfigCommand(
          tenantId = tenantId,
          scope = WorkItemConfigScope.TENANT,
          projectId = null,
          issueTypeApiId = issueType.apiId.value,
          workflowApiId = workflow.apiId.value,
          createFields = invalidCreateFields.jsonObject,
          statuses = listOf(IssueTypeConfigStatusInput(status.apiId.value, isInitial = true)),
        )

      coEvery { catalog.findIssueType(tenantId, issueType.apiId.value, null) } returns issueType
      coEvery { workflows.findWorkflow(tenantId, workflow.apiId.value) } returns workflow
      coEvery { catalog.findStatus(tenantId, status.apiId.value) } returns status

      shouldThrow<InvalidRequestException> {
        IssueTypeConfigService(configs, catalog, workflows).create(command)
      }

      coVerify(exactly = 0) { configs.createConfig(any()) }
    }

    "rejects workflow transition with unavailable from status" {
      val tenantId = UUID.randomUUID()
      val configs = mockk<IssueTypeConfigRepository>()
      val catalog = mockk<WorkItemCatalogRepository>()
      val workflows = mockk<WorkflowConfigurationRepository>()
      val now = OffsetDateTime.now(ZoneOffset.UTC)
      val issueType = sampleIssueType(tenantId)
      val workflow = sampleWorkflow(tenantId)
      val status = sampleStatus(tenantId)
      val command =
        CreateIssueTypeConfigCommand(
          tenantId = tenantId,
          scope = WorkItemConfigScope.TENANT,
          projectId = null,
          issueTypeApiId = issueType.apiId.value,
          workflowApiId = workflow.apiId.value,
          createFields = createFields.jsonObject,
          statuses = listOf(IssueTypeConfigStatusInput(status.apiId.value, isInitial = true)),
        )
      val invalidTransition =
        one.ztd.workbench.agile.workitem.model.WorkflowTransitionRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("trn"),
          tenantId = tenantId,
          workflowId = workflow.id,
          name = "Start",
          fromStatusId = UUID.randomUUID(),
          fromStatusApiId = PublicId.new("sts"),
          toStatusId = status.id,
          toStatusApiId = status.apiId,
          rank = 100,
          preconditionAst = JsonObject(emptyMap()),
          fields = createFields.jsonObject,
          isActive = true,
          createdAt = now,
          updatedAt = now,
        )

      coEvery { catalog.findIssueType(tenantId, issueType.apiId.value, null) } returns issueType
      coEvery { workflows.findWorkflow(tenantId, workflow.apiId.value) } returns workflow
      coEvery { catalog.findStatus(tenantId, status.apiId.value) } returns status
      coEvery { workflows.listTransitions(tenantId, workflow.id) } returns listOf(invalidTransition)

      shouldThrow<InvalidRequestException> {
        IssueTypeConfigService(configs, catalog, workflows).create(command)
      }

      coVerify(exactly = 0) { configs.createConfig(any()) }
    }

    "create accepts global transition with null from status" {
      val tenantId = UUID.randomUUID()
      val configs = mockk<IssueTypeConfigRepository>()
      val catalog = mockk<WorkItemCatalogRepository>()
      val workflows = mockk<WorkflowConfigurationRepository>()
      val now = OffsetDateTime.now(ZoneOffset.UTC)
      val issueType = sampleIssueType(tenantId)
      val workflow = sampleWorkflow(tenantId)
      val status = sampleStatus(tenantId)
      val command =
        CreateIssueTypeConfigCommand(
          tenantId = tenantId,
          scope = WorkItemConfigScope.TENANT,
          projectId = null,
          issueTypeApiId = issueType.apiId.value,
          workflowApiId = workflow.apiId.value,
          createFields = createFields.jsonObject,
          statuses = listOf(IssueTypeConfigStatusInput(status.apiId.value, isInitial = true)),
        )
      val created =
        sampleConfigDetails(tenantId)
          .copy(
            statuses =
              listOf(
                IssueTypeConfigStatusRecord(
                  id = UUID.randomUUID(),
                  tenantId = tenantId,
                  issueTypeConfigId = UUID.randomUUID(),
                  statusId = status.id,
                  statusApiId = status.apiId,
                  code = "todo",
                  name = "To Do",
                  statusGroup = WorkItemStatusGroup.TODO,
                  isInitial = true,
                  isTerminal = false,
                  rank = 100,
                )
              )
          )
      val globalTransition =
        one.ztd.workbench.agile.workitem.model.WorkflowTransitionRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("trn"),
          tenantId = tenantId,
          workflowId = workflow.id,
          name = "Create",
          fromStatusId = null,
          fromStatusApiId = null,
          toStatusId = status.id,
          toStatusApiId = status.apiId,
          rank = 100,
          preconditionAst = JsonObject(emptyMap()),
          fields = createFields.jsonObject,
          isActive = true,
          createdAt = now,
          updatedAt = now,
        )

      coEvery { catalog.findIssueType(tenantId, issueType.apiId.value, null) } returns issueType
      coEvery { workflows.findWorkflow(tenantId, workflow.apiId.value) } returns workflow
      coEvery { catalog.findStatus(tenantId, status.apiId.value) } returns status
      coEvery { workflows.listTransitions(tenantId, workflow.id) } returns listOf(globalTransition)
      coEvery { configs.createConfig(command) } returns created

      IssueTypeConfigService(configs, catalog, workflows).create(command) shouldBe created
    }
  })

private fun sampleConfigDetails(tenantId: UUID): IssueTypeConfigDetails {
  val now = OffsetDateTime.now(ZoneOffset.UTC)
  val config =
    IssueTypeConfigRecord(
      id = UUID.randomUUID(),
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
    )
  return IssueTypeConfigDetails(config = config, statuses = emptyList(), properties = emptyList())
}

private fun sampleIssueType(tenantId: UUID): IssueTypeRecord {
  val now = OffsetDateTime.now(ZoneOffset.UTC)
  return IssueTypeRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("typ"),
    tenantId = tenantId,
    projectId = null,
    scope = WorkItemConfigScope.TENANT,
    code = "task",
    name = "Task",
    description = null,
    icon = null,
    color = null,
    rank = 1,
    isActive = true,
    createdAt = now,
    updatedAt = now,
  )
}

private fun sampleWorkflow(tenantId: UUID): WorkflowRecord {
  val now = OffsetDateTime.now(ZoneOffset.UTC)
  return WorkflowRecord(
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
}

private fun sampleStatus(tenantId: UUID): IssueStatusRecord {
  val now = OffsetDateTime.now(ZoneOffset.UTC)
  return IssueStatusRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("sts"),
    tenantId = tenantId,
    code = "todo",
    name = "To Do",
    statusGroup = WorkItemStatusGroup.TODO,
    rank = 1,
    color = null,
    isTerminal = false,
    isActive = true,
    createdAt = now,
    updatedAt = now,
  )
}

private fun sampleProperty(tenantId: UUID): PropertyDefinitionRecord {
  val now = OffsetDateTime.now(ZoneOffset.UTC)
  return PropertyDefinitionRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("fld"),
    tenantId = tenantId,
    code = "severity",
    name = "Severity",
    description = null,
    dataType = WorkItemPropertyDataType.TEXT,
    isSystem = false,
    isArray = false,
    validationSchema = JsonObject(emptyMap()),
    searchConfig = JsonObject(emptyMap()),
    isActive = true,
    createdAt = now,
    updatedAt = now,
  )
}
