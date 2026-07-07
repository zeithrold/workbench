package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.workitem.IssueTypeConfigRepository
import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.access.WorkItemAccessEvaluationContext
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import java.util.UUID

internal data class CommentPermissionRequest(
  val tenantId: UUID,
  val projectId: UUID,
  val actorUserId: UUID,
  val workItemApiId: String,
)

internal object WorkItemCommentPermissionSupport {
  suspend fun evaluationContext(
    repository: WorkItemRepository,
    configs: IssueTypeConfigRepository,
    accessPolicy: WorkItemAccessPolicyEngine,
    request: CommentPermissionRequest,
  ): CommentEvaluationContext {
    val issue =
      requireWorkItem(repository, request.tenantId, request.projectId, request.workItemApiId)
    val config = requireIssueTypeConfig(configs, request.tenantId, issue.issueTypeConfigApiId.value)
    val projectApiId = requireProjectApiId(repository, request.tenantId, request.projectId)
    val properties = repository.listPropertyValues(request.tenantId, issue.id)
    return CommentEvaluationContext(
      issueTypeConfigId = config.config.id,
      context =
        WorkItemAccessEvaluationContext(
          actor =
            accessPolicy.resolveActor(
              request.tenantId,
              request.projectId,
              request.actorUserId,
            ),
          workItem = issue,
          issueTypeConfigId = config.config.id,
          projectApiId = projectApiId,
          properties = properties,
        ),
    )
  }

  private suspend fun requireWorkItem(
    repository: WorkItemRepository,
    tenantId: UUID,
    projectId: UUID,
    workItemApiId: String,
  ): WorkItemRecord =
    repository.findByApiId(tenantId, projectId, workItemApiId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND)

  private suspend fun requireIssueTypeConfig(
    configs: IssueTypeConfigRepository,
    tenantId: UUID,
    configApiId: String,
  ): IssueTypeConfigDetails =
    configs.findConfig(tenantId, configApiId)
      ?: throw ResourceNotFoundException(
        WorkbenchErrorCode.RESOURCE_WORK_ITEM_TYPE_CONFIG_NOT_FOUND
      )

  private suspend fun requireProjectApiId(
    repository: WorkItemRepository,
    tenantId: UUID,
    projectId: UUID,
  ): String =
    repository.resolveProjectApiId(tenantId, projectId)?.value
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PROJECT_NOT_FOUND)
}

internal data class CommentEvaluationContext(
  val issueTypeConfigId: UUID,
  val context: WorkItemAccessEvaluationContext,
)
