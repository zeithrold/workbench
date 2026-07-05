package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.errors.requireFound
import ink.doa.workbench.core.common.errors.requireValid
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.IssueTypeConfigRepository
import ink.doa.workbench.core.workitem.IssueSubtypeConstraintRepository
import ink.doa.workbench.core.workitem.WorkItemCatalogRepository
import ink.doa.workbench.core.workitem.WorkflowConfigurationRepository
import ink.doa.workbench.core.workitem.model.CreateIssueStatusCommand
import ink.doa.workbench.core.workitem.model.CreateIssueSubtypeConstraintCommand
import ink.doa.workbench.core.workitem.model.CreateIssueTypeCommand
import ink.doa.workbench.core.workitem.model.CreateIssueTypeConfigCommand
import ink.doa.workbench.core.workitem.model.CreatePropertyDefinitionCommand
import ink.doa.workbench.core.workitem.model.CreateWorkflowCommand
import ink.doa.workbench.core.workitem.model.CreateWorkflowTransitionCommand
import ink.doa.workbench.core.workitem.model.EffectiveIssueTypeConfig
import ink.doa.workbench.core.workitem.model.IssueStatusRecord
import ink.doa.workbench.core.workitem.model.IssueSubtypeConstraintRecord
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.IssueTypeConfigPropertyRecord
import ink.doa.workbench.core.workitem.model.IssueTypeConfigRecord
import ink.doa.workbench.core.workitem.model.IssueTypeRecord
import ink.doa.workbench.core.workitem.model.PropertyDefinitionRecord
import ink.doa.workbench.core.workitem.model.WorkItemConfigScope
import ink.doa.workbench.core.workitem.model.WorkflowRecord
import ink.doa.workbench.core.workitem.model.WorkflowTransitionRecord
import ink.doa.workbench.core.workitem.query.WorkItemConditionJson
import ink.doa.workbench.core.workitem.template.TransitionFieldsParser
import ink.doa.workbench.core.workitem.template.TransitionFieldsValidator
import ink.doa.workbench.core.workitem.template.WorkItemValueTemplateTarget
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class WorkItemCatalogService(private val repository: WorkItemCatalogRepository) {
  suspend fun createStatus(command: CreateIssueStatusCommand): IssueStatusRecord =
    repository.createStatus(command)

  suspend fun listStatuses(tenantId: UUID): List<IssueStatusRecord> =
    repository.listStatuses(tenantId)

  suspend fun deactivateStatus(
    tenantId: UUID,
    statusApiIdOrCode: String,
    actorUserId: UUID,
  ): IssueStatusRecord = repository.deactivateStatus(tenantId, statusApiIdOrCode, actorUserId)

  suspend fun createProperty(command: CreatePropertyDefinitionCommand): PropertyDefinitionRecord =
    repository.createProperty(command)

  suspend fun listProperties(tenantId: UUID): List<PropertyDefinitionRecord> =
    repository.listProperties(tenantId)

  suspend fun deactivateProperty(
    tenantId: UUID,
    propertyApiIdOrCode: String,
    actorUserId: UUID,
  ): PropertyDefinitionRecord =
    repository.deactivateProperty(tenantId, propertyApiIdOrCode, actorUserId)

  suspend fun createIssueType(command: CreateIssueTypeCommand): IssueTypeRecord {
    validateScope(command.scope, command.projectId)
    return repository.createIssueType(command)
  }

  suspend fun listIssueTypes(tenantId: UUID, projectId: UUID? = null): List<IssueTypeRecord> =
    repository.listIssueTypes(tenantId, projectId)

  suspend fun deactivateIssueType(
    tenantId: UUID,
    issueTypeApiIdOrCode: String,
    actorUserId: UUID,
    projectId: UUID? = null,
  ): IssueTypeRecord =
    repository.deactivateIssueType(tenantId, issueTypeApiIdOrCode, actorUserId, projectId)
}

@Service
class IssueSubtypeConstraintService(
  private val repository: IssueSubtypeConstraintRepository,
  private val catalog: WorkItemCatalogRepository,
) {
  suspend fun create(command: CreateIssueSubtypeConstraintCommand): IssueSubtypeConstraintRecord {
    command.projectId?.let {
      requireFound(
        catalog.findIssueType(command.tenantId, command.parentIssueTypeApiId, it) != null,
        WorkbenchErrorCode.RESOURCE_WORK_ITEM_TYPE_NOT_FOUND,
      )
      requireFound(
        catalog.findIssueType(command.tenantId, command.childIssueTypeApiId, it) != null,
        WorkbenchErrorCode.RESOURCE_WORK_ITEM_TYPE_NOT_FOUND,
      )
    } ?: run {
      requireFound(
        catalog.findIssueType(command.tenantId, command.parentIssueTypeApiId, null) != null,
        WorkbenchErrorCode.RESOURCE_WORK_ITEM_TYPE_NOT_FOUND,
      )
      requireFound(
        catalog.findIssueType(command.tenantId, command.childIssueTypeApiId, null) != null,
        WorkbenchErrorCode.RESOURCE_WORK_ITEM_TYPE_NOT_FOUND,
      )
    }
    return repository.create(command)
  }

  suspend fun list(
    tenantId: UUID,
    projectId: UUID? = null,
  ): List<IssueSubtypeConstraintRecord> = repository.list(tenantId, projectId)

  suspend fun deactivate(
    tenantId: UUID,
    constraintId: UUID,
    actorUserId: UUID,
  ): IssueSubtypeConstraintRecord = repository.deactivate(tenantId, constraintId, actorUserId)
}

@Service
class WorkflowConfigurationService(
  private val repository: WorkflowConfigurationRepository,
  private val catalog: WorkItemCatalogRepository,
  private val configs: IssueTypeConfigRepository,
  private val clock: Clock,
) {
  private val transitionFieldsParser = TransitionFieldsParser()

  suspend fun createWorkflow(command: CreateWorkflowCommand): WorkflowRecord =
    repository.createWorkflow(command)

  suspend fun listWorkflows(tenantId: UUID): List<WorkflowRecord> =
    repository.listWorkflows(tenantId)

  suspend fun publishWorkflow(tenantId: UUID, workflowApiIdOrCode: String): WorkflowRecord {
    val workflow =
      repository.findWorkflow(tenantId, workflowApiIdOrCode)
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORKFLOW_NOT_FOUND)
    return repository.publishWorkflow(tenantId, workflow.id, OffsetDateTime.now(clock))
  }

  suspend fun deactivateWorkflow(
    tenantId: UUID,
    workflowApiIdOrCode: String,
    actorUserId: UUID,
  ): WorkflowRecord = repository.deactivateWorkflow(tenantId, workflowApiIdOrCode, actorUserId)

  suspend fun createTransition(command: CreateWorkflowTransitionCommand): WorkflowTransitionRecord {
    val workflow = findRequiredWorkflow(repository, command.tenantId, command.workflowApiId)
    requireUnpublishedWorkflow(workflow)
    requireExistingStatus(catalog, command.tenantId, command.fromStatusApiId)
    requireExistingStatus(catalog, command.tenantId, command.toStatusApiId)
    validateTransitionAgainstActiveConfigs(command, workflow)
    val canonicalCommand =
      command.copy(
        permissionCondition = WorkItemConditionJson.canonicalize(command.permissionCondition),
        preconditionAst = WorkItemConditionJson.canonicalize(command.preconditionAst),
      )
    return repository.createTransition(canonicalCommand)
  }

  suspend fun deactivateTransition(
    tenantId: UUID,
    workflowApiIdOrCode: String,
    transitionApiId: String,
  ): WorkflowTransitionRecord {
    val workflow = findRequiredWorkflow(repository, tenantId, workflowApiIdOrCode)
    findRequiredTransition(repository, tenantId, transitionApiId, workflow.id)
    requireUnpublishedWorkflow(workflow)
    return repository.deactivateTransition(tenantId, transitionApiId)
  }

  private suspend fun validateTransitionAgainstActiveConfigs(
    command: CreateWorkflowTransitionCommand,
    workflow: WorkflowRecord,
  ) {
    val template = transitionFieldsParser.parse(command.fields)
    val activeConfigs =
      configs.listConfigs(command.tenantId).filter {
        it.config.workflowId == workflow.id && it.config.isActive && it.config.validTo == null
      }
    if (activeConfigs.isEmpty()) {
      TransitionFieldsValidator.validateEnvelope(template)
      return
    }
    activeConfigs.forEach { config ->
      val statusIds = config.statuses.map { it.statusApiId.value }.toSet()
      val fromStatusAvailable =
        command.fromStatusApiId == null || command.fromStatusApiId in statusIds
      if (command.toStatusApiId !in statusIds || !fromStatusAvailable) {
        throw InvalidRequestException(
          WorkbenchErrorCode.WORKFLOW_TRANSITION_STATUS_UNAVAILABLE,
          "Transition references a status outside active type config ${config.config.apiId.value}.",
        )
      }
      TransitionFieldsValidator.validate(template, config)
    }
  }

  suspend fun listTransitions(
    tenantId: UUID,
    workflowApiIdOrCode: String,
  ): List<WorkflowTransitionRecord> {
    val workflow =
      repository.findWorkflow(tenantId, workflowApiIdOrCode)
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORKFLOW_NOT_FOUND)
    return repository.listTransitions(tenantId, workflow.id)
  }
}

@Service
class IssueTypeConfigService(
  private val configs: IssueTypeConfigRepository,
  private val catalog: WorkItemCatalogRepository,
  private val workflows: WorkflowConfigurationRepository,
) {
  private val createFieldsParser = TransitionFieldsParser()

  suspend fun create(command: CreateIssueTypeConfigCommand): IssueTypeConfigDetails {
    validateScope(command.scope, command.projectId)
    validateBindings(command)
    validateCreateFields(command)
    val created = configs.createConfig(command)
    validateWorkflowTransitions(created)
    return created
  }

  suspend fun list(tenantId: UUID, projectId: UUID? = null): List<IssueTypeConfigDetails> =
    configs.listConfigs(tenantId, projectId)

  suspend fun resolveEffective(
    tenantId: UUID,
    projectId: UUID,
    issueTypeApiIdOrCode: String,
  ): EffectiveIssueTypeConfig =
    configs.resolveEffective(tenantId, projectId, issueTypeApiIdOrCode)
      ?: throw ResourceNotFoundException(
        WorkbenchErrorCode.RESOURCE_WORK_ITEM_TYPE_CONFIG_NOT_FOUND
      )

  private suspend fun validateBindings(command: CreateIssueTypeConfigCommand) {
    validateBindingShape(command)
    validateBindingReferences(command)
  }

  private fun validateBindingShape(command: CreateIssueTypeConfigCommand) {
    requireValid(
      command.statuses.count { it.isInitial } == 1,
      WorkbenchErrorCode.WORK_ITEM_CONFIG_INITIAL_STATUS_REQUIRED,
    )
    requireValid(
      command.statuses.map { it.statusApiId }.distinct().size == command.statuses.size,
      WorkbenchErrorCode.WORK_ITEM_CONFIG_DUPLICATE_STATUS,
    )
    requireValid(
      command.properties.map { it.propertyApiId }.distinct().size == command.properties.size,
      WorkbenchErrorCode.WORK_ITEM_CONFIG_DUPLICATE_PROPERTY,
    )
  }

  private suspend fun validateBindingReferences(command: CreateIssueTypeConfigCommand) {
    requireFound(
      catalog.findIssueType(command.tenantId, command.issueTypeApiId, command.projectId) != null,
      WorkbenchErrorCode.RESOURCE_WORK_ITEM_TYPE_NOT_FOUND,
    )
    requireFound(
      workflows.findWorkflow(command.tenantId, command.workflowApiId) != null,
      WorkbenchErrorCode.RESOURCE_WORKFLOW_NOT_FOUND,
    )
    command.statuses.forEach {
      requireFound(
        catalog.findStatus(command.tenantId, it.statusApiId) != null,
        WorkbenchErrorCode.RESOURCE_WORK_ITEM_STATUS_NOT_FOUND,
      )
    }
    command.properties.forEach {
      requireFound(
        catalog.findProperty(command.tenantId, it.propertyApiId) != null,
        WorkbenchErrorCode.RESOURCE_WORK_ITEM_PROPERTY_NOT_FOUND,
      )
    }
  }

  private suspend fun validateWorkflowTransitions(details: IssueTypeConfigDetails) {
    val statusIds = details.statuses.map { it.statusId }.toSet()
    val transitions = workflows.listTransitions(details.config.tenantId, details.config.workflowId)
    val invalid = transitions.firstOrNull { transition ->
      transition.toStatusId !in statusIds ||
        (transition.fromStatusId != null && transition.fromStatusId !in statusIds)
    }
    if (invalid != null) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORKFLOW_TRANSITION_STATUS_UNAVAILABLE,
        "Transition ${invalid.apiId.value} references a status outside the type config.",
      )
    }
  }

  private suspend fun validateCreateFields(command: CreateIssueTypeConfigCommand) {
    val configId = UUID.randomUUID()
    val properties =
      command.properties.map { input ->
        val record =
          catalog.findProperty(command.tenantId, input.propertyApiId)
            ?: throw ResourceNotFoundException(
              WorkbenchErrorCode.RESOURCE_WORK_ITEM_PROPERTY_NOT_FOUND
            )
        IssueTypeConfigPropertyRecord(
          id = UUID.randomUUID(),
          tenantId = command.tenantId,
          issueTypeConfigId = configId,
          propertyId = record.id,
          propertyApiId = record.apiId,
          code = record.code,
          name = record.name,
          dataType = record.dataType,
          validationOverride = input.validationOverride,
          rank = input.rank,
          displayConfig = input.displayConfig,
        )
      }
    val template = createFieldsParser.parseCreateFields(command.createFields)
    TransitionFieldsValidator.validate(
      template,
      IssueTypeConfigDetails(
        config =
          IssueTypeConfigRecord(
            id = configId,
            apiId = PublicId.new("itc"),
            tenantId = command.tenantId,
            scope = command.scope,
            projectId = command.projectId,
            issueTypeId = UUID.randomUUID(),
            issueTypeApiId = PublicId.new("typ"),
            workflowId = UUID.randomUUID(),
            workflowApiId = PublicId.new("wfl"),
            version = 1,
            nameOverride = command.nameOverride,
            iconOverride = command.iconOverride,
            colorOverride = command.colorOverride,
            rank = command.rank,
            isActive = true,
            validFrom = OffsetDateTime.now(),
            validTo = null,
            createdBy = command.createdBy,
            createdAt = OffsetDateTime.now(),
            updatedAt = OffsetDateTime.now(),
            createFields = command.createFields,
          ),
        statuses = emptyList(),
        properties = properties,
      ),
      expectedTarget = WorkItemValueTemplateTarget.CREATE,
    )
  }
}

private fun validateScope(scope: WorkItemConfigScope, projectId: UUID?) {
  if (scope == WorkItemConfigScope.TENANT && projectId != null) {
    throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_CONFIG_SCOPE_PROJECT_FORBIDDEN)
  }
  if (scope == WorkItemConfigScope.PROJECT && projectId == null) {
    throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_CONFIG_SCOPE_PROJECT_REQUIRED)
  }
}

private suspend fun findRequiredWorkflow(
  repository: WorkflowConfigurationRepository,
  tenantId: UUID,
  workflowApiIdOrCode: String,
): WorkflowRecord =
  repository.findWorkflow(tenantId, workflowApiIdOrCode)
    ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORKFLOW_NOT_FOUND)

private suspend fun findRequiredTransition(
  repository: WorkflowConfigurationRepository,
  tenantId: UUID,
  transitionApiId: String,
  workflowId: UUID,
) {
  val transition =
    repository.findTransition(tenantId, transitionApiId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORKFLOW_TRANSITION_NOT_FOUND)
  requireFound(
    transition.workflowId == workflowId,
    WorkbenchErrorCode.RESOURCE_WORKFLOW_TRANSITION_NOT_FOUND,
  )
}

private fun requireUnpublishedWorkflow(workflow: WorkflowRecord) {
  requireValid(
    workflow.publishedAt == null,
    WorkbenchErrorCode.WORKFLOW_PUBLISHED_UPDATE_FORBIDDEN,
  )
}

private suspend fun requireExistingStatus(
  catalog: WorkItemCatalogRepository,
  tenantId: UUID,
  statusApiId: String?,
) {
  statusApiId ?: return
  requireFound(
    catalog.findStatus(tenantId, statusApiId) != null,
    WorkbenchErrorCode.RESOURCE_WORK_ITEM_STATUS_NOT_FOUND,
  )
}
