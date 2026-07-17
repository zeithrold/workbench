package one.ztd.workbench.agile.workitem

import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import one.ztd.workbench.agile.workitem.model.CreateIssueStatusCommand
import one.ztd.workbench.agile.workitem.model.CreateIssueTypeCommand
import one.ztd.workbench.agile.workitem.model.CreateIssueTypeConfigCommand
import one.ztd.workbench.agile.workitem.model.CreateWorkflowCommand
import one.ztd.workbench.agile.workitem.model.CreateWorkflowTransitionCommand
import one.ztd.workbench.agile.workitem.model.IssueStatusRecord
import one.ztd.workbench.agile.workitem.model.IssueTypeConfigStatusInput
import one.ztd.workbench.agile.workitem.model.IssueTypeRecord
import one.ztd.workbench.agile.workitem.model.WorkItemConfigScope
import one.ztd.workbench.agile.workitem.model.WorkItemStatusGroup
import one.ztd.workbench.agile.workitem.model.WorkflowRecord
import org.springframework.stereotype.Service

private const val TASK_TYPE_CODE = "task"
private const val DEFAULT_WORKFLOW_CODE = "default-task"

private data class DefaultStatuses(
  val todo: IssueStatusRecord,
  val inProgress: IssueStatusRecord,
  val done: IssueStatusRecord,
)

private data class DefaultStatusSpec(
  val code: String,
  val name: String,
  val group: WorkItemStatusGroup,
  val rank: Int,
  val terminal: Boolean,
)

private data class DefaultTransitionSpec(
  val name: String,
  val fromStatusId: String,
  val toStatusId: String,
  val rank: Int,
)

@Service
class TenantDefaultWorkItemTemplateService(
  private val catalog: WorkItemCatalogService,
  private val workflows: WorkflowConfigurationService,
  private val configs: IssueTypeConfigService,
) {
  suspend fun ensureProvisioned(tenantId: UUID, createdBy: UUID?) {
    val statuses = ensureStatuses(tenantId)
    val task = ensureTaskType(tenantId)
    val workflow = ensureWorkflow(tenantId, createdBy)
    ensureTransitions(tenantId, workflow, statuses)
    ensureConfig(tenantId, createdBy, task, workflow, statuses)
    if (workflow.publishedAt == null) workflows.publishWorkflow(tenantId, workflow.apiId.value)
  }

  private suspend fun ensureStatuses(tenantId: UUID) =
    DefaultStatuses(
      todo =
        ensureStatus(
          tenantId,
          DefaultStatusSpec("todo", "To Do", WorkItemStatusGroup.TODO, 100, false),
        ),
      inProgress =
        ensureStatus(
          tenantId,
          DefaultStatusSpec(
            "in_progress",
            "In Progress",
            WorkItemStatusGroup.IN_PROGRESS,
            200,
            false,
          ),
        ),
      done =
        ensureStatus(
          tenantId,
          DefaultStatusSpec("done", "Done", WorkItemStatusGroup.DONE, 300, true),
        ),
    )

  private suspend fun ensureTaskType(tenantId: UUID): IssueTypeRecord =
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

  private suspend fun ensureWorkflow(tenantId: UUID, createdBy: UUID?): WorkflowRecord =
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

  private suspend fun ensureTransitions(
    tenantId: UUID,
    workflow: WorkflowRecord,
    statuses: DefaultStatuses,
  ) {
    val transitions = workflows.listTransitions(tenantId, workflow.apiId.value)
    ensureTransition(
      tenantId,
      workflow.apiId.value,
      transitions,
      DefaultTransitionSpec(
        "Start progress",
        statuses.todo.apiId.value,
        statuses.inProgress.apiId.value,
        100,
      ),
    )
    ensureTransition(
      tenantId,
      workflow.apiId.value,
      transitions,
      DefaultTransitionSpec(
        "Complete",
        statuses.inProgress.apiId.value,
        statuses.done.apiId.value,
        200,
      ),
    )
    ensureTransition(
      tenantId,
      workflow.apiId.value,
      transitions,
      DefaultTransitionSpec(
        "Reopen",
        statuses.done.apiId.value,
        statuses.inProgress.apiId.value,
        300,
      ),
    )
  }

  private suspend fun ensureConfig(
    tenantId: UUID,
    createdBy: UUID?,
    task: IssueTypeRecord,
    workflow: WorkflowRecord,
    statuses: DefaultStatuses,
  ) {
    val activeConfigExists = configs.list(tenantId).any { it.config.isActiveFor(task) }
    if (!activeConfigExists) {
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
              IssueTypeConfigStatusInput(statuses.todo.apiId.value, isInitial = true, rank = 100),
              IssueTypeConfigStatusInput(statuses.inProgress.apiId.value, rank = 200),
              IssueTypeConfigStatusInput(statuses.done.apiId.value, isTerminal = true, rank = 300),
            ),
        )
      )
    }
  }

  private suspend fun ensureStatus(
    tenantId: UUID,
    spec: DefaultStatusSpec,
  ) =
    catalog.listStatuses(tenantId).firstOrNull { it.code == spec.code }
      ?: catalog.createStatus(
        CreateIssueStatusCommand(
          tenantId,
          spec.code,
          spec.name,
          spec.group,
          spec.rank,
          isTerminal = spec.terminal,
        )
      )

  private suspend fun ensureTransition(
    tenantId: UUID,
    workflowId: String,
    existing: List<one.ztd.workbench.agile.workitem.model.WorkflowTransitionRecord>,
    spec: DefaultTransitionSpec,
  ) {
    if (existing.any { it.connects(spec.fromStatusId, spec.toStatusId) }) return
    workflows.createTransition(
      CreateWorkflowTransitionCommand(
        tenantId,
        workflowId,
        spec.name,
        spec.fromStatusId,
        spec.toStatusId,
        spec.rank,
      )
    )
  }

  private fun one.ztd.workbench.agile.workitem.model.WorkflowTransitionRecord.connects(
    fromStatusId: String,
    toStatusId: String,
  ): Boolean = fromStatusApiId?.value == fromStatusId && toStatusApiId?.value == toStatusId

  private fun one.ztd.workbench.agile.workitem.model.IssueTypeConfigRecord.isActiveFor(
    task: IssueTypeRecord
  ): Boolean =
    scope == WorkItemConfigScope.TENANT &&
      issueTypeApiId == task.apiId &&
      isActive &&
      validTo == null
}
