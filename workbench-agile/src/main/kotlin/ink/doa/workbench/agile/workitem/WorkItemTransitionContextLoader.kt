package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.model.TransitionRequest
import java.util.UUID
import org.springframework.stereotype.Component

@Component
class WorkItemTransitionContextLoader(
  private val repository: WorkItemRepository,
  private val mutationSupport: WorkItemMutationSupport,
  private val transitionValidator: WorkItemTransitionValidator,
) {
  suspend fun loadForIssue(
    tenantId: UUID,
    projectId: UUID,
    workItemApiId: String,
    actorUserId: UUID,
  ): WorkItemTransitionContext {
    val issue =
      repository.findByApiId(tenantId, projectId, workItemApiId)
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND)
    return load(issue, actorUserId)
  }

  suspend fun load(
    issue: ink.doa.workbench.core.workitem.model.WorkItemRecord,
    actorUserId: UUID,
  ): WorkItemTransitionContext {
    val config = mutationSupport.requireConfig(issue.tenantId, issue.issueTypeConfigApiId.value)
    val currentProperties = repository.listPropertyValues(issue.tenantId, issue.id)
    val conditionContext =
      transitionValidator.conditionContext(issue, actorUserId, currentProperties)
    val permissionContext =
      WorkItemFieldPermissionContext(
        tenantId = issue.tenantId,
        projectId = issue.projectId,
        actorUserId = actorUserId,
        operation = FieldPermissionOperation.UPDATE,
      )
    val templateContext =
      mutationSupport.templateContext(
        WorkItemTemplateContextRequest(
          tenantId = issue.tenantId,
          projectId = issue.projectId,
          actorUserId = actorUserId,
          workItem = issue,
          currentProperties = currentProperties,
        )
      )
    return WorkItemTransitionContext(
      tenantId = issue.tenantId,
      projectId = issue.projectId,
      actorUserId = actorUserId,
      issue = issue,
      config = config,
      currentProperties = currentProperties,
      conditionContext = conditionContext,
      templateContext = templateContext,
      permissionContext = permissionContext,
    )
  }

  suspend fun load(request: TransitionRequest): WorkItemTransitionContext =
    loadForIssue(
      tenantId = request.tenantId,
      projectId = request.projectId,
      workItemApiId = request.workItemApiId,
      actorUserId = request.actorUserId,
    )
}
