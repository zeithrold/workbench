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
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.IssueTypeConfigPropertyRecord
import ink.doa.workbench.core.workitem.model.TransitionWorkItemCommand
import ink.doa.workbench.core.workitem.model.UpdateWorkItemCommand
import ink.doa.workbench.core.workitem.model.WorkItemMutationResult
import ink.doa.workbench.core.workitem.model.WorkItemPropertyDataType
import ink.doa.workbench.core.workitem.model.WorkItemPropertyValue
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemTransitionOption
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Service

@Service
@Suppress("TooManyFunctions")
class WorkItemService(
  private val repository: WorkItemRepository,
  private val configs: IssueTypeConfigRepository,
  private val workflows: WorkflowConfigurationRepository,
  private val events: DomainEventPublisher,
) {
  private val conditions = WorkItemConditionEvaluator()

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
    val values = normalizeProperties(config.config, command.properties, includeDefaults = true)
    return repository
      .create(
        command = command,
        issueTypeId = config.config.config.issueTypeId,
        issueTypeConfigId = config.config.config.id,
        initialStatusId = initial.statusId,
        propertyValues = values,
      )
      .also { publish(it) }
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
    val values = normalizeProperties(config, command.properties, includeDefaults = false)
    return repository.update(command, values).also { publish(it) }
  }

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
    return workflows
      .listTransitions(tenantId, config.config.workflowId)
      .filter { it.fromStatusId == issue.statusId }
      .map { transition ->
        val enabled =
          canUseTransition(transition.permissionCondition, context) &&
            canUseTransition(transition.preconditionAst, context) &&
            transition.toStatusId in config.statuses.map { it.statusId }.toSet()
        WorkItemTransitionOption(
          id = transition.apiId,
          name = transition.name,
          toStatusId =
            transition.toStatusApiId
              ?: throw ResourceNotFoundException(
                WorkbenchErrorCode.RESOURCE_WORK_ITEM_STATUS_NOT_FOUND
              ),
          enabled = enabled,
          reason = if (enabled) null else "Transition conditions are not satisfied.",
          requiredProperties = transition.requiredProperties,
          optionalProperties = transition.optionalProperties,
        )
      }
  }

  @Suppress("ThrowsCount")
  suspend fun transition(command: TransitionWorkItemCommand): WorkItemMutationResult {
    val issue = get(command.tenantId, command.projectId, command.workItemApiId)
    val config = requireConfig(command.tenantId, issue.issueTypeConfigApiId.value)
    val transition =
      workflows.findTransition(command.tenantId, command.transitionApiId)
        ?: throw ResourceNotFoundException(
          WorkbenchErrorCode.RESOURCE_WORKFLOW_TRANSITION_NOT_FOUND
        )
    if (
      transition.workflowId != config.config.workflowId || transition.fromStatusId != issue.statusId
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

    val defaults = transition.propertyDefaults.toMap()
    val submitted = defaults + command.properties
    val missing =
      transition.requiredProperties.propertyKeys().firstOrNull { key ->
        val code =
          config.properties.find { it.code == key || it.propertyApiId.value == key }?.code ?: key
        key !in submitted && code !in submitted && code !in currentProperties
      }
    if (missing != null) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_PROPERTY_REQUIRED,
        "Required transition property is missing: $missing",
      )
    }
    val values = normalizeProperties(config, submitted, includeDefaults = false)
    return repository
      .transition(
        command = command,
        fromStatusId = issue.statusId,
        toStatusId = transition.toStatusId,
        transitionId = transition.id,
        propertyValues = values,
      )
      .also { publish(it) }
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

  private fun canUseTransition(ast: JsonObject, context: WorkItemConditionContext): Boolean =
    runCatching { conditions.evaluate(ast, context) }.getOrDefault(false)

  private suspend fun requireConfig(tenantId: UUID, configApiId: String): IssueTypeConfigDetails =
    configs.findConfig(tenantId, configApiId)
      ?: throw ResourceNotFoundException(
        WorkbenchErrorCode.RESOURCE_WORK_ITEM_TYPE_CONFIG_NOT_FOUND
      )

  private fun normalizeProperties(
    config: IssueTypeConfigDetails,
    input: Map<String, JsonElement>,
    includeDefaults: Boolean,
  ): List<WorkItemPropertyValue> {
    val byKey =
      config.properties
        .flatMap { property ->
          listOf(property.propertyApiId.value to property, property.code to property)
        }
        .toMap()
    val values =
      if (includeDefaults) {
        config.properties
          .mapNotNull { property -> property.defaultValue?.let { property.code to it } }
          .toMap() + input
      } else {
        input
      }
    val requiredMissing =
      if (includeDefaults) {
        config.properties.firstOrNull {
          it.isRequired && it.code !in values && it.propertyApiId.value !in values
        }
      } else {
        null
      }
    if (requiredMissing != null) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_PROPERTY_REQUIRED,
        "Required property is missing: ${requiredMissing.code}",
      )
    }
    return values.map { (key, value) ->
      val property =
        byKey[key]
          ?: throw InvalidRequestException(
            WorkbenchErrorCode.WORK_ITEM_PROPERTY_UNAVAILABLE,
            "Property is not available in this config: $key",
          )
      validateValue(property, value)
      WorkItemPropertyValue(
        propertyId = property.propertyId,
        propertyApiId = property.propertyApiId,
        code = property.code,
        dataType = property.dataType,
        value = value,
      )
    }
  }

  private fun validateValue(property: IssueTypeConfigPropertyRecord, value: JsonElement) {
    if (value is JsonNull) return
    val valid =
      when (property.dataType) {
        WorkItemPropertyDataType.TEXT,
        WorkItemPropertyDataType.LONG_TEXT,
        WorkItemPropertyDataType.DATE,
        WorkItemPropertyDataType.DATETIME,
        WorkItemPropertyDataType.URL,
        WorkItemPropertyDataType.USER,
        WorkItemPropertyDataType.PROJECT,
        WorkItemPropertyDataType.ISSUE,
        WorkItemPropertyDataType.SINGLE_SELECT ->
          value is JsonPrimitive && value.contentOrNull != null
        WorkItemPropertyDataType.NUMBER -> value is JsonPrimitive && value.doubleOrNull != null
        WorkItemPropertyDataType.BOOLEAN -> value is JsonPrimitive && value.booleanOrNull != null
        WorkItemPropertyDataType.MULTI_SELECT,
        WorkItemPropertyDataType.MULTI_USER -> value is JsonArray
        WorkItemPropertyDataType.JSON -> true
      }
    if (!valid) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_PROPERTY_VALUE_INVALID,
        "Invalid value for property ${property.code}.",
      )
    }
  }

  private fun JsonObject.toMap(): Map<String, JsonElement> = entries.associate {
    it.key to it.value
  }

  private fun JsonElement.propertyKeys(): Set<String> =
    when (this) {
      is JsonArray -> mapNotNull { it.jsonPrimitive.contentOrNull }.toSet()
      is JsonObject -> keys
      else -> emptySet()
    }
}
