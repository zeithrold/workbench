package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.workitem.IssueTypeConfigRepository
import ink.doa.workbench.core.workitem.WorkItemCatalogRepository
import ink.doa.workbench.core.workitem.WorkflowConfigurationRepository
import ink.doa.workbench.core.workitem.model.CreateIssueTypeConfigCommand
import ink.doa.workbench.core.workitem.model.IssueTypeConfigStatusInput
import ink.doa.workbench.core.workitem.model.WorkItemConfigScope
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.mockk.coVerify
import io.mockk.mockk
import java.util.UUID
import kotlinx.serialization.json.Json
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
  })
