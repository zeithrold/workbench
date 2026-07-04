package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.IssueTypeConfigRepository
import ink.doa.workbench.core.workitem.WorkItemCatalogRepository
import ink.doa.workbench.core.workitem.WorkflowConfigurationRepository
import ink.doa.workbench.core.workitem.model.CreateIssueTypeConfigCommand
import ink.doa.workbench.core.workitem.model.EffectiveIssueTypeConfig
import ink.doa.workbench.core.workitem.model.IssueStatusRecord
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.IssueTypeConfigRecord
import ink.doa.workbench.core.workitem.model.IssueTypeConfigStatusInput
import ink.doa.workbench.core.workitem.model.IssueTypeRecord
import ink.doa.workbench.core.workitem.model.WorkItemConfigScope
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
import ink.doa.workbench.core.workitem.model.WorkflowRecord
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
      coEvery { configs.createConfig(command) } returns created
      coEvery { workflows.listTransitions(tenantId, created.config.workflowId) } returns emptyList()

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
