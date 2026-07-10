package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.workitem.model.CreateIssueStatusCommand
import ink.doa.workbench.core.workitem.model.CreateIssueTypeCommand
import ink.doa.workbench.core.workitem.model.CreateIssueTypeConfigCommand
import ink.doa.workbench.core.workitem.model.CreateWorkflowCommand
import ink.doa.workbench.core.workitem.model.CreateWorkflowTransitionCommand
import ink.doa.workbench.core.workitem.model.IssueTypeConfigStatusInput
import ink.doa.workbench.core.workitem.model.WorkItemConfigScope
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.springframework.stereotype.Service

private const val TASK_TYPE_CODE = "task"
private const val DEFAULT_WORKFLOW_CODE = "default-task"

@Service
class TenantDefaultWorkItemTemplateService(
  private val catalog: WorkItemCatalogService,
  private val workflows: WorkflowConfigurationService,
  private val configs: IssueTypeConfigService,
) {
  @Suppress("ComplexCondition", "LongMethod")
  suspend fun ensureProvisioned(tenantId: UUID, createdBy: UUID?) {
    val todo = ensureStatus(tenantId, "todo", "To Do", WorkItemStatusGroup.TODO, 100, false)
    val inProgress =
      ensureStatus(
        tenantId,
        "in_progress",
        "In Progress",
        WorkItemStatusGroup.IN_PROGRESS,
        200,
        false,
      )
    val done = ensureStatus(tenantId, "done", "Done", WorkItemStatusGroup.DONE, 300, true)
    val task =
      catalog.listIssueTypes(tenantId).firstOrNull { it.code == TASK_TYPE_CODE }
        ?: catalog.createIssueType(
          CreateIssueTypeCommand(
            tenantId = tenantId,
            scope = WorkItemConfigScope.TENANT,
            code = TASK_TYPE_CODE,
            name = "Task",
            description = "Default work item type.",
          )
        )
    val workflow =
      workflows.listWorkflows(tenantId).firstOrNull { it.code == DEFAULT_WORKFLOW_CODE }
        ?: workflows.createWorkflow(
          CreateWorkflowCommand(
            tenantId = tenantId,
            code = DEFAULT_WORKFLOW_CODE,
            name = "Default Task Workflow",
            description = "Default workflow for Task work items.",
            createdBy = createdBy,
          )
        )
    val transitions = workflows.listTransitions(tenantId, workflow.apiId.value)
    ensureTransition(
      tenantId,
      workflow.apiId.value,
      transitions,
      "Start progress",
      todo.apiId.value,
      inProgress.apiId.value,
      100,
    )
    ensureTransition(
      tenantId,
      workflow.apiId.value,
      transitions,
      "Complete",
      inProgress.apiId.value,
      done.apiId.value,
      200,
    )
    ensureTransition(
      tenantId,
      workflow.apiId.value,
      transitions,
      "Reopen",
      done.apiId.value,
      inProgress.apiId.value,
      300,
    )
    if (
      configs.list(tenantId).none {
        it.config.scope == WorkItemConfigScope.TENANT &&
          it.config.issueTypeApiId == task.apiId &&
          it.config.isActive &&
          it.config.validTo == null
      }
    ) {
      configs.create(
        CreateIssueTypeConfigCommand(
          tenantId = tenantId,
          scope = WorkItemConfigScope.TENANT,
          projectId = null,
          issueTypeApiId = task.apiId.value,
          workflowApiId = workflow.apiId.value,
          createdBy = createdBy,
          createFields =
            Json.parseToJsonElement(
                """{"version":1,"resource":"work_item","target":"create","fields":{"title":{"participation":"required"}}}"""
              )
              .jsonObject,
          statuses =
            listOf(
              IssueTypeConfigStatusInput(todo.apiId.value, isInitial = true, rank = 100),
              IssueTypeConfigStatusInput(inProgress.apiId.value, rank = 200),
              IssueTypeConfigStatusInput(done.apiId.value, isTerminal = true, rank = 300),
            ),
        )
      )
    }
    if (workflow.publishedAt == null) workflows.publishWorkflow(tenantId, workflow.apiId.value)
  }

  @Suppress("LongParameterList")
  private suspend fun ensureStatus(
    tenantId: UUID,
    code: String,
    name: String,
    group: WorkItemStatusGroup,
    rank: Int,
    terminal: Boolean,
  ) =
    catalog.listStatuses(tenantId).firstOrNull { it.code == code }
      ?: catalog.createStatus(
        CreateIssueStatusCommand(tenantId, code, name, group, rank, isTerminal = terminal)
      )

  @Suppress("ComplexCondition", "LongParameterList")
  private suspend fun ensureTransition(
    tenantId: UUID,
    workflowId: String,
    existing: List<ink.doa.workbench.core.workitem.model.WorkflowTransitionRecord>,
    name: String,
    fromStatusId: String,
    toStatusId: String,
    rank: Int,
  ) {
    if (
      existing.any {
        it.fromStatusApiId?.value == fromStatusId && it.toStatusApiId?.value == toStatusId
      }
    )
      return
    workflows.createTransition(
      CreateWorkflowTransitionCommand(tenantId, workflowId, name, fromStatusId, toStatusId, rank)
    )
  }
}
