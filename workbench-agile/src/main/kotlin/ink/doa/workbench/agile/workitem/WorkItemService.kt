package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.workitem.CreateWorkItemPersistenceCommand
import ink.doa.workbench.core.workitem.IssueSubtypeConstraintRepository
import ink.doa.workbench.core.workitem.IssueTypeConfigRepository
import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.core.workitem.model.DeleteWorkItemCommand
import ink.doa.workbench.core.workitem.model.UpdateWorkItemCommand
import ink.doa.workbench.core.workitem.model.WorkItemCreateFormOption
import ink.doa.workbench.core.workitem.model.WorkItemMutationResult
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.template.TransitionFieldsParser
import ink.doa.workbench.core.workitem.template.toWirePath
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class WorkItemService(
  private val repository: WorkItemRepository,
  private val configs: IssueTypeConfigRepository,
  private val subtypeConstraints: IssueSubtypeConstraintRepository,
  private val mutationSupport: WorkItemMutationSupport,
  private val fieldMutationSupport: WorkItemFieldMutationSupport,
) {
  private val transitionFieldsParser = TransitionFieldsParser()

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
    val parentIssue = resolveParentIssue(command)
    validateCreateParentConstraint(command, config.config.config.issueTypeId, parentIssue)
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
    val fieldsTemplate = transitionFieldsParser.parseCreateFields(config.config.config.createFields)
    val reconciled =
      fieldMutationSupport.reconcileCreate(
        command = command,
        config = config.config,
        fieldsTemplate = fieldsTemplate,
        templateContext = templateContext,
        permissionContext = permissionContext,
      )
    val effectiveCommand =
      WorkItemPropertySupport.applyCreateSystemFields(command, reconciled.systemFields)
    fieldMutationSupport.descriptionAttachments.rejectCreateDescriptionReferences(
      effectiveCommand.description
    )
    val values =
      WorkItemPropertySupport.normalizeProperties(config.config, reconciled.propertyValues)
    return repository
      .create(
        CreateWorkItemPersistenceCommand(
          command = effectiveCommand,
          issueTypeId = config.config.config.issueTypeId,
          issueTypeConfigId = config.config.config.id,
          initialStatusId = initial.statusId,
          parentIssueId = parentIssue?.id,
          propertyValues = values,
        )
      )
      .also { mutationSupport.publish(it) }
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
    val fieldsTemplate = transitionFieldsParser.parseCreateFields(config.config.config.createFields)
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
    val editableFields =
      fieldsTemplate.fields
        .filter { (field, spec) ->
          fieldMutationSupport.permissions.isFormFieldEditable(permissionContext, field, spec)
        }
        .map { (field, _) -> field.toWirePath() }
    val fieldMeta =
      fieldMutationSupport.reconciler.buildFieldMeta(
        template = fieldsTemplate,
        config = config.config,
        templateContext = templateContext,
        permissionContext = permissionContext,
      )
    return WorkItemCreateFormOption(
      issueTypeId = config.config.config.issueTypeApiId,
      initialStatusId = initial.statusApiId,
      fields = config.config.config.createFields,
      editableFields = editableFields,
      fieldMeta = fieldMeta,
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
    fieldMutationSupport.reconciler.assertWritableProperties(
      permissionContext,
      config,
      WorkItemPropertySupport.run { command.properties.filterPropertyInputs() },
    )
    fieldMutationSupport.reconciler.assertWritableSystemFields(
      permissionContext,
      mapOf(
        "title" to command.title,
        "description" to command.description,
        "assignee" to command.assigneeApiId,
        "priority" to command.priorityApiId,
        "sprint" to command.sprintApiId,
      ),
    )
    val values =
      WorkItemPropertySupport.normalizeProperties(
        config,
        WorkItemPropertySupport.run { command.properties.filterPropertyInputs() },
      )
    val effectiveCommand = WorkItemPropertySupport.applyDescriptionProcessing(command)
    fieldMutationSupport.descriptionAttachments.validateReferences(
      tenantId = command.tenantId,
      projectId = command.projectId,
      workItemApiId = command.workItemApiId,
      issueId = issue.id,
      descriptionHtml = effectiveCommand.description,
    )
    return repository.update(effectiveCommand, values).also { mutationSupport.publish(it) }
  }

  suspend fun delete(command: DeleteWorkItemCommand): WorkItemMutationResult =
    repository.softDelete(command).also { mutationSupport.publish(it) }

  private suspend fun resolveParentIssue(command: CreateWorkItemCommand): WorkItemRecord? =
    command.parentWorkItemApiId?.let {
      repository.findByApiId(command.tenantId, it)
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND)
    }

  private suspend fun validateCreateParentConstraint(
    command: CreateWorkItemCommand,
    issueTypeId: UUID,
    parentIssue: WorkItemRecord?,
  ) {
    if (parentIssue == null) {
      validateRootSubtypeAllowed(command, issueTypeId)
      return
    }
    validateChildSubtypeAllowed(command, issueTypeId, parentIssue)
  }

  private suspend fun validateRootSubtypeAllowed(
    command: CreateWorkItemCommand,
    issueTypeId: UUID,
  ) {
    if (subtypeConstraints.isChildOnlyType(command.tenantId, command.projectId, issueTypeId)) {
      throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_SUBTYPE_PARENT_REQUIRED)
    }
  }

  private suspend fun validateChildSubtypeAllowed(
    command: CreateWorkItemCommand,
    issueTypeId: UUID,
    parentIssue: WorkItemRecord,
  ) {
    if (parentIssue.projectId != command.projectId) {
      throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_SUBTYPE_CROSS_PROJECT_FORBIDDEN)
    }
    subtypeConstraints.findAllowedChildType(
      tenantId = command.tenantId,
      projectId = command.projectId,
      parentIssueTypeId = parentIssue.issueTypeId,
      childIssueTypeId = issueTypeId,
    ) ?: throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_SUBTYPE_NOT_ALLOWED)
  }

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
