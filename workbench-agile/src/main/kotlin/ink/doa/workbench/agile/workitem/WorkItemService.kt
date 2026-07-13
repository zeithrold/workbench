package ink.doa.workbench.agile.workitem

import ink.doa.workbench.agile.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.agile.workitem.model.DeleteWorkItemCommand
import ink.doa.workbench.agile.workitem.model.UpdateWorkItemCommand
import ink.doa.workbench.agile.workitem.model.WorkItemCreateFormOption
import ink.doa.workbench.agile.workitem.model.WorkItemMutationResult
import ink.doa.workbench.agile.workitem.model.WorkItemRecord
import ink.doa.workbench.identity.UserRepository
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.ResourceNotFoundException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class WorkItemService(
  private val repository: WorkItemRepository,
  private val configs: IssueTypeConfigRepository,
  private val users: UserRepository,
  private val createParentGuard: WorkItemCreateParentGuard,
  private val mutationSupport: WorkItemMutationSupport,
  private val fieldPipeline: WorkItemFieldMutationPipeline,
) {
  suspend fun create(command: CreateWorkItemCommand): WorkItemMutationResult {
    val config =
      configs.resolveEffective(command.tenantId, command.projectId, command.issueTypeApiId)
        ?: throw ResourceNotFoundException(
          WorkbenchErrorCode.RESOURCE_WORK_ITEM_TYPE_CONFIG_NOT_FOUND
        )
    val initial =
      config.config.statuses.singleOrNull { it.isInitial }
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_CONFIG_INITIAL_STATUS_REQUIRED
        )
    val parentIssue =
      createParentGuard.resolveAndValidate(command, config.config.config.issueTypeId)
    val (permissionContext, templateContext) = createMutationContexts(command)
    val plan =
      fieldPipeline.applyCreate(
        command = command,
        config = config.config,
        templateContext = templateContext,
        permissionContext = permissionContext,
        createFields = config.config.config.createFields,
      )
    return repository.create(
      ink.doa.workbench.agile.workitem.CreateWorkItemPersistenceCommand(
        command = plan.command,
        issueTypeId = config.config.config.issueTypeId,
        issueTypeConfigId = config.config.config.id,
        initialStatusId = initial.statusId,
        parentIssueId = parentIssue?.id,
        propertyValues = plan.propertyValues,
      )
    )
  }

  suspend fun availableCreateForm(
    tenantId: UUID,
    projectId: UUID,
    issueTypeApiId: String,
    actorUserId: UUID,
  ): WorkItemCreateFormOption {
    val config =
      configs.resolveEffective(tenantId, projectId, issueTypeApiId)
        ?: throw ResourceNotFoundException(
          WorkbenchErrorCode.RESOURCE_WORK_ITEM_TYPE_CONFIG_NOT_FOUND
        )
    val initial =
      config.config.statuses.singleOrNull { it.isInitial }
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_CONFIG_INITIAL_STATUS_REQUIRED
        )
    val permissionContext =
      fieldPermissionContext(tenantId, projectId, actorUserId, FieldPermissionOperation.CREATE)
    val templateContext =
      mutationSupport.templateContext(
        WorkItemTemplateContextRequest(
          tenantId = tenantId,
          projectId = projectId,
          actorUserId = actorUserId,
          reporterUserId = actorUserId,
        )
      )
    val formPlan =
      fieldPipeline.planCreateForm(
        config = config.config,
        createFields = config.config.config.createFields,
        templateContext = templateContext,
        permissionContext = permissionContext,
      )
    return WorkItemCreateFormOption(
      issueTypeId = config.config.config.issueTypeApiId,
      initialStatusId = initial.statusApiId,
      fields = config.config.config.createFields,
      editableFields = formPlan.editableWirePaths,
      fieldMeta = formPlan.fieldMeta,
    )
  }

  suspend fun get(
    tenantId: UUID,
    projectId: UUID,
    workItemApiId: String,
  ): WorkItemRecord =
    repository.findByApiId(tenantId, projectId, workItemApiId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND)

  suspend fun list(
    tenantId: UUID,
    projectId: UUID,
    limit: Int = 50,
    offset: Long = 0,
  ): List<WorkItemRecord> = repository.listByProject(tenantId, projectId, limit, offset)

  suspend fun update(command: UpdateWorkItemCommand): WorkItemMutationResult {
    val issue = get(command.tenantId, command.projectId, command.workItemApiId)
    val config = mutationSupport.requireConfig(command.tenantId, issue.issueTypeConfigApiId.value)
    val permissionContext =
      fieldPermissionContext(
        command.tenantId,
        command.projectId,
        command.actorUserId,
        FieldPermissionOperation.UPDATE,
      )
    fieldPipeline.engine.assertPatch(
      PatchMutationContext(
        config = config,
        permissionContext = permissionContext,
        propertyInputs = WorkItemPropertySupport.run { command.properties.filterPropertyInputs() },
        systemFieldInputs =
          mapOf(
            "title" to command.title,
            "description" to command.description,
            "assignee" to command.assigneeApiId,
            "priority" to command.priorityApiId,
            "sprint" to command.sprintApiId,
          ),
      )
    )
    val values =
      WorkItemPropertySupport.normalizeProperties(
        config,
        WorkItemPropertySupport.run { command.properties.filterPropertyInputs() },
      )
    val effectiveCommand = WorkItemPropertySupport.applyDescriptionProcessing(command)
    return repository.update(effectiveCommand, values)
  }

  suspend fun delete(command: DeleteWorkItemCommand): WorkItemMutationResult =
    repository.softDelete(command)

  private suspend fun createMutationContexts(
    command: CreateWorkItemCommand
  ): Pair<
    WorkItemFieldPermissionContext,
    ink.doa.workbench.agile.workitem.template.WorkItemValueTemplateContext,
  > {
    val permissionContext =
      fieldPermissionContext(
        command.tenantId,
        command.projectId,
        command.actorUserId,
        FieldPermissionOperation.CREATE,
      )
    val templateContext =
      mutationSupport.templateContext(
        WorkItemTemplateContextRequest(
          tenantId = command.tenantId,
          projectId = command.projectId,
          actorUserId = command.actorUserId,
          reporterUserId = command.reporterId,
        )
      )
    return permissionContext to templateContext
  }

  private suspend fun fieldPermissionContext(
    tenantId: UUID,
    projectId: UUID,
    actorUserId: UUID,
    operation: FieldPermissionOperation,
  ): WorkItemFieldPermissionContext {
    val actorUserApiId =
      users.findById(actorUserId)?.apiId?.value
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND)
    return WorkItemFieldPermissionContext(
      tenantId = tenantId,
      projectId = projectId,
      actorUserId = actorUserId,
      actorUserApiId = actorUserApiId,
      operation = operation,
    )
  }
}
