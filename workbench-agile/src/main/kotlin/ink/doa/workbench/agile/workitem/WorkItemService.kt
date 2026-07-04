package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.PermissionDeniedException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.port.messaging.DomainEventPublisher
import ink.doa.workbench.core.workitem.IssueTypeConfigRepository
import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.WorkflowConfigurationRepository
import ink.doa.workbench.core.workitem.events.WorkItemDomainEvents
import ink.doa.workbench.core.workitem.events.WorkItemMutationEvent
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommentCommand
import ink.doa.workbench.core.workitem.model.DeleteWorkItemCommand
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.IssueTypeConfigPropertyRecord
import ink.doa.workbench.core.workitem.model.TransitionWorkItemCommand
import ink.doa.workbench.core.workitem.model.UpdateWorkItemCommand
import ink.doa.workbench.core.workitem.model.WorkItemCreateFormOption
import ink.doa.workbench.core.workitem.model.WorkItemMutationResult
import ink.doa.workbench.core.workitem.model.WorkItemPropertyValue
import ink.doa.workbench.core.workitem.model.WorkItemPropertyValueValidator
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemTransitionOption
import ink.doa.workbench.core.workitem.richtext.RichTextProcessor
import ink.doa.workbench.core.workitem.template.TransitionFieldsParser
import ink.doa.workbench.core.workitem.template.WorkItemValueTemplateContext
import ink.doa.workbench.core.workitem.template.toWirePath
import java.time.Clock
import java.util.UUID
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.springframework.stereotype.Service

@Service
@Suppress("TooManyFunctions")
class WorkItemService(
  private val repository: WorkItemRepository,
  private val configs: IssueTypeConfigRepository,
  private val workflows: WorkflowConfigurationRepository,
  private val events: DomainEventPublisher,
  private val fieldMutationReconciler: WorkItemFieldMutationReconciler,
  private val fieldPermissions: WorkItemFieldPermissionService,
  private val commentService: WorkItemCommentService,
  private val clock: Clock = Clock.systemUTC(),
) {
  private val conditions = WorkItemConditionEvaluator()
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
      templateContext(
        tenantId = command.tenantId,
        projectId = command.projectId,
        actorUserId = command.actorUserId,
        reporterUserId = command.reporterId,
      )
    val fieldsTemplate = transitionFieldsParser.parseCreateFields(config.config.config.createFields)
    val reconciled =
      fieldMutationReconciler.reconcileCreate(
        template = fieldsTemplate,
        config = config.config,
        templateContext = templateContext,
        userProperties = createFieldInputs(command),
        permissionContext = permissionContext,
      )
    val effectiveCommand = applyCreateSystemFields(command, reconciled.systemFields)
    val values = normalizeProperties(config.config, reconciled.propertyValues)
    return repository
      .create(
        command = effectiveCommand,
        issueTypeId = config.config.config.issueTypeId,
        issueTypeConfigId = config.config.config.id,
        initialStatusId = initial.statusId,
        propertyValues = values,
      )
      .also { publish(it) }
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
      templateContext(
        tenantId = tenantId,
        projectId = projectId,
        actorUserId = actorUserId,
        reporterUserId = actorUserId,
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
    val config = requireConfig(command.tenantId, issue.issueTypeConfigApiId.value)
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
      command.properties.filterPropertyInputs(),
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
    val values = normalizeProperties(config, command.properties.filterPropertyInputs())
    val effectiveCommand = applyDescriptionProcessing(command)
    return repository.update(effectiveCommand, values).also { publish(it) }
  }

  suspend fun delete(command: DeleteWorkItemCommand): WorkItemMutationResult =
    repository.softDelete(command).also { publish(it) }

  suspend fun availableTransitions(
    tenantId: UUID,
    projectId: UUID,
    workItemApiId: String,
    actorUserId: UUID,
  ): List<WorkItemTransitionOption> {
    val issue = get(tenantId, projectId, workItemApiId)
    val config = requireConfig(tenantId, issue.issueTypeConfigApiId.value)
    val currentProperties = repository.listPropertyValues(tenantId, issue.id)
    val context = conditionContext(issue, actorUserId, currentProperties)
    val permissionContext =
      fieldPermissionContext(tenantId, projectId, actorUserId, FieldPermissionOperation.UPDATE)
    return workflows
      .listTransitions(tenantId, config.config.workflowId)
      .filter { it.fromStatusId == null || it.fromStatusId == issue.statusId }
      .map { transition ->
        val fieldsTemplate = runCatching { transitionFieldsParser.parse(transition.fields) }
        val templateContext =
          templateContext(
            tenantId = tenantId,
            projectId = projectId,
            actorUserId = actorUserId,
            workItem = issue,
            currentProperties = currentProperties,
          )
        val targetStatusAvailable = transition.toStatusId in config.statuses.map { it.statusId }.toSet()
        val permissionCondition =
          checkCondition(
            transition.permissionCondition,
            context,
            failedReason = "Transition permission condition is not satisfied.",
            invalidReason = "Transition permission condition is invalid.",
          )
        val precondition =
          checkCondition(
            transition.preconditionAst,
            context,
            failedReason = "Transition precondition is not satisfied.",
            invalidReason = "Transition precondition is invalid.",
          )
        val enabled =
          targetStatusAvailable &&
            permissionCondition.enabled &&
            precondition.enabled &&
            fieldsTemplate.isSuccess
        val editableFields =
          fieldsTemplate
            .getOrNull()
            ?.fields
            ?.filter { (field, spec) ->
              fieldPermissions.isFormFieldEditable(permissionContext, field, spec)
            }
            ?.map { (field, _) -> field.toWirePath() }
            .orEmpty()
        val fieldMeta =
          fieldsTemplate
            .getOrNull()
            ?.let {
              fieldMutationReconciler.buildFieldMeta(
                template = it,
                config = config,
                templateContext = templateContext,
                permissionContext = permissionContext,
              )
            }
            .orEmpty()
        val reason =
          when {
            !targetStatusAvailable -> "Transition target status is not available in this type config."
            permissionCondition.reason != null -> permissionCondition.reason
            precondition.reason != null -> precondition.reason
            fieldsTemplate.isFailure -> "Transition field requirements are invalid."
            else -> null
          }
        WorkItemTransitionOption(
          id = transition.apiId,
          name = transition.name,
          fromStatusId = transition.fromStatusApiId,
          toStatusId =
            transition.toStatusApiId
              ?: throw ResourceNotFoundException(
                WorkbenchErrorCode.RESOURCE_WORK_ITEM_STATUS_NOT_FOUND
              ),
          enabled = enabled,
          reason = reason,
          fields = transition.fields,
          editableFields = editableFields,
          fieldMeta = fieldMeta,
          commentMeta =
            fieldsTemplate
              .getOrNull()
              ?.let { fieldMutationReconciler.buildCommentMeta(it.comment, templateContext) },
        )
      }
  }

  @Suppress("ThrowsCount", "LongMethod")
  suspend fun transition(command: TransitionWorkItemCommand): WorkItemMutationResult {
    val issue = get(command.tenantId, command.projectId, command.workItemApiId)
    val config = requireConfig(command.tenantId, issue.issueTypeConfigApiId.value)
    val transition =
      workflows.findTransition(command.tenantId, command.transitionApiId)
        ?: throw ResourceNotFoundException(
          WorkbenchErrorCode.RESOURCE_WORKFLOW_TRANSITION_NOT_FOUND
        )
    if (
      transition.workflowId != config.config.workflowId ||
        (transition.fromStatusId != null && transition.fromStatusId != issue.statusId)
    ) {
      throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_TRANSITION_STATUS_MISMATCH)
    }
    if (config.statuses.none { it.statusId == transition.toStatusId }) {
      throw InvalidRequestException(WorkbenchErrorCode.WORKFLOW_TRANSITION_STATUS_UNAVAILABLE)
    }

    val currentProperties = repository.listPropertyValues(command.tenantId, issue.id)
    val context = conditionContext(issue, command.actorUserId, currentProperties)
    if (!conditions.evaluate(transition.permissionCondition, context)) {
      throw PermissionDeniedException(WorkbenchErrorCode.WORK_ITEM_TRANSITION_PERMISSION_DENIED)
    }
    if (!conditions.evaluate(transition.preconditionAst, context)) {
      throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_TRANSITION_PRECONDITION_FAILED)
    }

    val fieldsTemplate = transitionFieldsParser.parse(transition.fields)
    val templateContext =
      templateContext(
        tenantId = command.tenantId,
        projectId = command.projectId,
        actorUserId = command.actorUserId,
        workItem = issue,
        currentProperties = currentProperties,
      )
    val permissionContext =
      fieldPermissionContext(
        command.tenantId,
        command.projectId,
        command.actorUserId,
        FieldPermissionOperation.UPDATE,
      )
    val reconciled =
      fieldMutationReconciler.reconcileTransition(
        template = fieldsTemplate,
        config = config,
        templateContext = templateContext,
        currentProperties = currentProperties,
        userProperties = transitionFieldInputs(command),
        permissionContext = permissionContext,
      )
    val commentBody =
      fieldMutationReconciler.reconcileTransitionComment(
        spec = fieldsTemplate.comment,
        templateContext = templateContext,
        userComment = command.comment,
      )
    val effectiveCommand = applyTransitionSystemFields(command, reconciled.systemFields)
    val values = normalizeProperties(config, reconciled.propertyValues)
    val result =
      repository
        .transition(
          command = effectiveCommand,
          fromStatusId = issue.statusId,
          toStatusId = transition.toStatusId,
          transitionId = transition.id,
          propertyValues = values,
        )
        .also { publish(it) }
    if (commentBody != null) {
      commentService.create(
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
    return result
  }

  private fun publish(result: WorkItemMutationResult) {
    val spec =
      when (result.eventType) {
        WorkItemDomainEvents.Created.type -> WorkItemDomainEvents.Created
        WorkItemDomainEvents.Updated.type -> WorkItemDomainEvents.Updated
        WorkItemDomainEvents.Transitioned.type -> WorkItemDomainEvents.Transitioned
        else -> return
      }
    events.publish(
      spec = spec,
      key = result.workItem.apiId.value,
      payload = WorkItemMutationEvent.from(result.workItem),
    )
  }

  private suspend fun conditionContext(
    issue: WorkItemRecord,
    actorUserId: UUID,
    properties: Map<String, JsonElement>,
  ): WorkItemConditionContext =
    WorkItemConditionContext(
      workItem = issue,
      actorUserId = actorUserId,
      properties = properties,
      childIssuesNotDone =
        repository.countChildrenNotInStatusGroups(issue.tenantId, issue.id, setOf("done")),
    )

  private fun checkCondition(
    ast: JsonObject,
    context: WorkItemConditionContext,
    failedReason: String,
    invalidReason: String,
  ): TransitionConditionCheck =
    runCatching { conditions.evaluate(ast, context) }
      .fold(
        onSuccess = { TransitionConditionCheck(it, if (it) null else failedReason) },
        onFailure = { TransitionConditionCheck(false, invalidReason) },
      )

  private suspend fun requireConfig(tenantId: UUID, configApiId: String): IssueTypeConfigDetails =
    configs.findConfig(tenantId, configApiId)
      ?: throw ResourceNotFoundException(
        WorkbenchErrorCode.RESOURCE_WORK_ITEM_TYPE_CONFIG_NOT_FOUND
      )

  private fun normalizeProperties(
    config: IssueTypeConfigDetails,
    input: Map<String, JsonElement>,
  ): List<WorkItemPropertyValue> {
    if (input.isEmpty()) return emptyList()
    val byKey =
      config.properties
        .flatMap { property ->
          listOf(property.propertyApiId.value to property, property.code to property)
        }
        .toMap()
    return input.map { (key, value) ->
      val property =
        byKey[key]
          ?: throw InvalidRequestException(
            WorkbenchErrorCode.WORK_ITEM_PROPERTY_UNAVAILABLE,
            "Property is not available in this config: $key",
          )
      WorkItemPropertyValueValidator.validate(property, value)
      WorkItemPropertyValue(
        propertyId = property.propertyId,
        propertyApiId = property.propertyApiId,
        code = property.code,
        dataType = property.dataType,
        value = value,
      )
    }
  }

  private suspend fun templateContext(
    tenantId: UUID,
    projectId: UUID,
    actorUserId: UUID,
    reporterUserId: UUID? = null,
    workItem: WorkItemRecord? = null,
    currentProperties: Map<String, JsonElement> = emptyMap(),
  ): WorkItemValueTemplateContext {
    val currentUserApiId =
      repository.resolveUserApiId(actorUserId)
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND)
    val currentProjectApiId =
      repository.resolveProjectApiId(tenantId, projectId)
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PROJECT_NOT_FOUND)
    return WorkItemValueTemplateContext(
      tenantId = tenantId,
      projectId = projectId,
      currentUserApiId = currentUserApiId.value,
      currentProjectApiId = currentProjectApiId.value,
      actorUserId = actorUserId,
      reporterUserId = reporterUserId,
      workItem = workItem,
      currentProperties = currentProperties,
    )
  }

  private fun applyCreateSystemFields(
    command: CreateWorkItemCommand,
    systemFields: Map<String, String?>,
  ): CreateWorkItemCommand {
    val description = systemFields["description"] ?: command.description
    val processed = RichTextProcessor.processDescriptionInput(description)
    return command.copy(
      title = systemFields["title"] ?: command.title,
      description = processed?.html,
      descriptionPlainText = processed?.plainText,
      assigneeApiId = systemFields["assignee"] ?: command.assigneeApiId,
      priorityApiId = systemFields["priority"] ?: command.priorityApiId,
      sprintApiId = systemFields["sprint"] ?: command.sprintApiId,
    )
  }

  private fun applyTransitionSystemFields(
    command: TransitionWorkItemCommand,
    systemFields: Map<String, String?>,
  ): TransitionWorkItemCommand {
    val description = command.description ?: systemFields["description"]
    val processed = RichTextProcessor.processDescriptionInput(description)
    return command.copy(
      title = command.title ?: systemFields["title"],
      description = processed?.html,
      descriptionPlainText = processed?.plainText,
      assigneeApiId = command.assigneeApiId ?: systemFields["assignee"],
      priorityApiId = command.priorityApiId ?: systemFields["priority"],
      sprintApiId = command.sprintApiId ?: systemFields["sprint"],
    )
  }

  private fun applyDescriptionProcessing(command: UpdateWorkItemCommand): UpdateWorkItemCommand {
    if (command.description == null) return command
    val processed = RichTextProcessor.processDescriptionInput(command.description)
    return command.copy(
      description = processed?.html,
      descriptionPlainText = processed?.plainText,
    )
  }

  private fun transitionFieldInputs(command: TransitionWorkItemCommand): Map<String, JsonElement> {
    val inputs = linkedMapOf<String, JsonElement>()
    command.title?.let { inputs["title"] = JsonPrimitive(it) }
    command.description?.let { inputs["description"] = JsonPrimitive(it) }
    command.assigneeApiId?.let { inputs["assignee"] = JsonPrimitive(it) }
    command.priorityApiId?.let { inputs["priority"] = JsonPrimitive(it) }
    command.sprintApiId?.let { inputs["sprint"] = JsonPrimitive(it) }
    inputs.putAll(command.properties)
    return inputs
  }

  private fun createFieldInputs(command: CreateWorkItemCommand): Map<String, JsonElement> {
    val inputs = mutableMapOf<String, JsonElement>("title" to JsonPrimitive(command.title))
    command.description?.let { inputs["description"] = JsonPrimitive(it) }
    command.assigneeApiId?.let { inputs["assignee"] = JsonPrimitive(it) }
    command.priorityApiId?.let { inputs["priority"] = JsonPrimitive(it) }
    command.sprintApiId?.let { inputs["sprint"] = JsonPrimitive(it) }
    inputs.putAll(command.properties)
    return inputs
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

  private fun Map<String, JsonElement>.filterPropertyInputs(): Map<String, JsonElement> =
    filterKeys {
      it !in systemTemplateFields
    }

  private data class TransitionConditionCheck(val enabled: Boolean, val reason: String?)

  private companion object {
    val systemTemplateFields = setOf("title", "description", "assignee", "priority", "sprint")
  }
}
