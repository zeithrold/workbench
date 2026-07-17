package one.ztd.workbench.agile.workitem

import io.kotest.core.spec.style.StringSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import one.ztd.workbench.agile.workitem.model.CreateIssueStatusCommand
import one.ztd.workbench.agile.workitem.model.IssueStatusRecord
import one.ztd.workbench.agile.workitem.model.IssueTypeConfigDetails
import one.ztd.workbench.agile.workitem.model.IssueTypeRecord
import one.ztd.workbench.agile.workitem.model.WorkflowRecord
import one.ztd.workbench.agile.workitem.model.WorkflowTransitionRecord
import one.ztd.workbench.kernel.common.ids.PublicId

class TenantDefaultWorkItemTemplateServiceTest :
  StringSpec({
    "provisions and publishes the complete default template when tenant has no work item config" {
      val tenantId = UUID.randomUUID()
      val actorId = UUID.randomUUID()
      val catalog = mockk<WorkItemCatalogService>()
      val workflows = mockk<WorkflowConfigurationService>()
      val configs = mockk<IssueTypeConfigService>()
      val statuses =
        mapOf(
          "todo" to status("sta_01JABCDEFGHJKMNPQRSTVWXYZ0"),
          "in_progress" to status("sta_01JABCDEFGHJKMNPQRSTVWXYZ1"),
          "done" to status("sta_01JABCDEFGHJKMNPQRSTVWXYZ2"),
        )
      val task =
        mockk<IssueTypeRecord> {
          every { apiId } returns PublicId("typ_01JABCDEFGHJKMNPQRSTVWXYZ0")
        }
      val workflow =
        mockk<WorkflowRecord> {
          every { apiId } returns PublicId("wfl_01JABCDEFGHJKMNPQRSTVWXYZ0")
          every { publishedAt } returns null
        }

      coEvery { catalog.listStatuses(tenantId) } returns emptyList()
      coEvery { catalog.createStatus(any()) } answers
        {
          statuses.getValue(firstArg<CreateIssueStatusCommand>().code)
        }
      coEvery { catalog.listIssueTypes(tenantId) } returns emptyList()
      coEvery { catalog.createIssueType(any()) } returns task
      coEvery { workflows.listWorkflows(tenantId) } returns emptyList()
      coEvery { workflows.createWorkflow(any()) } returns workflow
      coEvery { workflows.listTransitions(tenantId, "wfl_01JABCDEFGHJKMNPQRSTVWXYZ0") } returns
        emptyList()
      coEvery { workflows.createTransition(any()) } returns mockk<WorkflowTransitionRecord>()
      coEvery { workflows.publishWorkflow(tenantId, "wfl_01JABCDEFGHJKMNPQRSTVWXYZ0") } returns
        workflow
      coEvery { configs.list(tenantId) } returns emptyList()
      coEvery { configs.create(any()) } returns mockk<IssueTypeConfigDetails>()

      TenantDefaultWorkItemTemplateService(catalog, workflows, configs)
        .ensureProvisioned(tenantId, actorId)

      coVerify(exactly = 3) { catalog.createStatus(any()) }
      coVerify(exactly = 3) { workflows.createTransition(any()) }
      coVerify(exactly = 1) { configs.create(any()) }
      coVerify(exactly = 1) {
        workflows.publishWorkflow(tenantId, "wfl_01JABCDEFGHJKMNPQRSTVWXYZ0")
      }
    }
  })

private fun status(apiId: String) =
  mockk<IssueStatusRecord> { every { this@mockk.apiId } returns PublicId(apiId) }
