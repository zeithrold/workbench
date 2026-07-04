package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
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
  private val mutationSupport: WorkItemMutationSupport,
  private val fieldMutationReconciler: WorkItemFieldMutationReconciler,
  private val fieldPermissions: WorkItemFieldPermissionService,
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
      fieldMutationReconciler.reconcileCreate(
        template = fieldsTemplate,
        config = config.config,
        templateContext = templateContext,
        userProperties = WorkItemPropertySupport.createFieldInputs(command),
        permissionContext = permissionContext,
      )
    val effectiveCommand =
      WorkItemPropertySupport.applyCreateSystemFields(command, reconciled.systemFields)
    val values =
      WorkItemPropertySupport.normalizeProperties(config.config, reconciled.propertyValues)
    return repository
      .create(
        command = effectiveCommand,
        issueTypeId = config.config.config.issueTypeId,
        issueTypeConfigId = config.config.config.id,
        initialStatusId = initial.statusId,
        propertyValues = values,
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
          fieldPermissions.isFormFieldEditable(permissionContext, field, spec)
        }
        .map { (field, _) -> field.toWirePath() }
    val fieldMeta =
      fieldMutationReconciler.buildFieldMeta(
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
    fieldMutationReconciler.assertWritableProperties(
      permissionContext,
      config,
      WorkItemPropertySupport.run { command.properties.filterPropertyInputs() },
    )
    fieldMutationReconciler.assertWritableSystemFields(
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
    return repository.update(effectiveCommand, values).also { mutationSupport.publish(it) }
  }

  suspend fun delete(command: DeleteWorkItemCommand): WorkItemMutationResult =
    repository.softDelete(command).also { mutationSupport.publish(it) }

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
