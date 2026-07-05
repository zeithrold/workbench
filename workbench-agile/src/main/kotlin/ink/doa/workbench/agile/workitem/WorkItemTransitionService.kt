package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.WorkflowConfigurationRepository
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommentCommand
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.TransitionWorkItemCommand
import ink.doa.workbench.core.workitem.model.WorkItemMutationResult
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemTransitionOption
import ink.doa.workbench.core.workitem.model.WorkflowTransitionRecord
import ink.doa.workbench.core.workitem.template.TransitionFieldsParser
import java.util.UUID
import kotlinx.serialization.json.JsonElement
import org.springframework.stereotype.Service

@Service
class WorkItemTransitionService(
  private val repository: WorkItemRepository,
  private val workflows: WorkflowConfigurationRepository,
  private val collaborators: WorkItemTransitionCollaborators,
  private val descriptionAttachmentValidator: WorkItemDescriptionAttachmentValidator,
) {
  private val transitionFieldsParser = TransitionFieldsParser()

  suspend fun availableTransitions(
    tenantId: UUID,
    projectId: UUID,
    workItemApiId: String,
    actorUserId: UUID,
  ): List<WorkItemTransitionOption> {
    val issue = get(tenantId, projectId, workItemApiId)
    val config =
      collaborators.mutationSupport.requireConfig(tenantId, issue.issueTypeConfigApiId.value)
    val currentProperties = repository.listPropertyValues(tenantId, issue.id)
    val context =
      collaborators.transitionValidator.conditionContext(issue, actorUserId, currentProperties)
    val permissionContext =
      fieldPermissionContext(tenantId, projectId, actorUserId, FieldPermissionOperation.UPDATE)
    val buildContext =
      TransitionOptionBuildContext(
        issue = issue,
        config = config,
        tenantId = tenantId,
        projectId = projectId,
        actorUserId = actorUserId,
        currentProperties = currentProperties,
        context = context,
        permissionContext = permissionContext,
      )
    return workflows
      .listTransitions(tenantId, config.config.workflowId)
      .filter { it.fromStatusId == null || it.fromStatusId == issue.statusId }
      .map { collaborators.transitionOptions.build(it, buildContext) }
  }

  suspend fun transition(command: TransitionWorkItemCommand): WorkItemMutationResult {
    val issue = get(command.tenantId, command.projectId, command.workItemApiId)
    val config =
      collaborators.mutationSupport.requireConfig(
        command.tenantId,
        issue.issueTypeConfigApiId.value,
      )
    val transition =
      workflows.findTransition(command.tenantId, command.transitionApiId)
        ?: throw ResourceNotFoundException(
          WorkbenchErrorCode.RESOURCE_WORKFLOW_TRANSITION_NOT_FOUND
        )
    collaborators.transitionValidator.requireTransitionApplicable(issue, config, transition)

    val currentProperties = repository.listPropertyValues(command.tenantId, issue.id)
    val context =
      collaborators.transitionValidator.conditionContext(
        issue,
        command.actorUserId,
        currentProperties,
      )
    collaborators.transitionValidator.requireTransitionPermission(transition, context)
    collaborators.transitionValidator.requireTransitionPrecondition(transition, context)

    return executeTransition(command, issue, config, transition, currentProperties)
  }

  private suspend fun executeTransition(
    command: TransitionWorkItemCommand,
    issue: WorkItemRecord,
    config: IssueTypeConfigDetails,
    transition: WorkflowTransitionRecord,
    currentProperties: Map<String, JsonElement>,
  ): WorkItemMutationResult {
    val fieldsTemplate = transitionFieldsParser.parse(transition.fields)
    val templateContext =
      collaborators.mutationSupport.templateContext(
        WorkItemTemplateContextRequest(
          tenantId = command.tenantId,
          projectId = command.projectId,
          actorUserId = command.actorUserId,
          workItem = issue,
          currentProperties = currentProperties,
        )
      )
    val permissionContext =
      fieldPermissionContext(
        command.tenantId,
        command.projectId,
        command.actorUserId,
        FieldPermissionOperation.UPDATE,
      )
    val reconciled =
      collaborators.fieldMutationReconciler.reconcileTransition(
        template = fieldsTemplate,
        config = config,
        templateContext = templateContext,
        currentProperties = currentProperties,
        userProperties = WorkItemPropertySupport.transitionFieldInputs(command),
        permissionContext = permissionContext,
      )
    val commentBody =
      collaborators.fieldMutationReconciler.reconcileTransitionComment(
        spec = fieldsTemplate.comment,
        templateContext = templateContext,
        userComment = command.comment,
      )
    val effectiveCommand =
      WorkItemPropertySupport.applyTransitionSystemFields(command, reconciled.systemFields)
    descriptionAttachmentValidator.validateReferences(
      tenantId = command.tenantId,
      projectId = command.projectId,
      workItemApiId = command.workItemApiId,
      issueId = issue.id,
      descriptionHtml = effectiveCommand.description,
    )
    val values = WorkItemPropertySupport.normalizeProperties(config, reconciled.propertyValues)
    val result =
      repository
        .transition(
          command = effectiveCommand,
          fromStatusId = issue.statusId,
          toStatusId = transition.toStatusId,
          transitionId = transition.id,
          propertyValues = values,
        )
        .also { collaborators.mutationSupport.publish(it) }
    createTransitionComment(command, transition, commentBody, result)
    return result
  }

  private suspend fun createTransitionComment(
    command: TransitionWorkItemCommand,
    transition: WorkflowTransitionRecord,
    commentBody: String?,
    result: WorkItemMutationResult,
  ) {
    if (commentBody == null) return
    collaborators.commentService.create(
      CreateWorkItemCommentCommand(
        tenantId = command.tenantId,
        projectId = command.projectId,
        workItemApiId = command.workItemApiId,
        authorId = command.actorUserId,
        body = commentBody,
        transitionId = transition.id,
        statusHistoryId = result.statusHistoryId,
      )
    )
  }

  private suspend fun get(
    tenantId: UUID,
    projectId: UUID,
    workItemApiId: String,
  ): WorkItemRecord =
    repository.findByApiId(tenantId, projectId, workItemApiId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND)

  private fun fieldPermissionContext(
    tenantId: UUID,
    projectId: UUID,
    actorUserId: UUID,
    operation: FieldPermissionOperation,
  ): WorkItemFieldPermissionContext =
    WorkItemFieldPermissionContext(
      tenantId = tenantId,
      projectId = projectId,
      actorUserId = actorUserId,
      operation = operation,
    )
}
