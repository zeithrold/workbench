package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.workitem.IssueTypeConfigRepository
import ink.doa.workbench.core.workitem.WorkItemCatalogRepository
import ink.doa.workbench.core.workitem.WorkflowConfigurationRepository
import ink.doa.workbench.core.workitem.model.CreateIssueStatusCommand
import ink.doa.workbench.core.workitem.model.CreateIssueTypeCommand
import ink.doa.workbench.core.workitem.model.CreateIssueTypeConfigCommand
import ink.doa.workbench.core.workitem.model.CreatePropertyDefinitionCommand
import ink.doa.workbench.core.workitem.model.CreateWorkflowCommand
import ink.doa.workbench.core.workitem.model.CreateWorkflowTransitionCommand
import ink.doa.workbench.core.workitem.model.EffectiveIssueTypeConfig
import ink.doa.workbench.core.workitem.model.IssueStatusRecord
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.IssueTypeRecord
import ink.doa.workbench.core.workitem.model.PropertyDefinitionRecord
import ink.doa.workbench.core.workitem.model.WorkItemConfigScope
import ink.doa.workbench.core.workitem.model.WorkflowRecord
import ink.doa.workbench.core.workitem.model.WorkflowTransitionRecord
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

  suspend fun createProperty(command: CreatePropertyDefinitionCommand): PropertyDefinitionRecord =
    repository.createProperty(command)

  suspend fun listProperties(tenantId: UUID): List<PropertyDefinitionRecord> =
    repository.listProperties(tenantId)

  suspend fun createIssueType(command: CreateIssueTypeCommand): IssueTypeRecord {
    validateScope(command.scope, command.projectId)
    return repository.createIssueType(command)
  }

  suspend fun listIssueTypes(tenantId: UUID, projectId: UUID? = null): List<IssueTypeRecord> =
    repository.listIssueTypes(tenantId, projectId)
}

@Service
class WorkflowConfigurationService(
  private val repository: WorkflowConfigurationRepository,
  private val catalog: WorkItemCatalogRepository,
  private val clock: Clock,
) {
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

  suspend fun createTransition(command: CreateWorkflowTransitionCommand): WorkflowTransitionRecord {
    catalog.findStatus(command.tenantId, command.fromStatusApiId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_STATUS_NOT_FOUND)
    catalog.findStatus(command.tenantId, command.toStatusApiId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_STATUS_NOT_FOUND)
    return repository.createTransition(command)
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
  suspend fun create(command: CreateIssueTypeConfigCommand): IssueTypeConfigDetails {
    validateScope(command.scope, command.projectId)
    validateBindings(command)
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

  @Suppress("ThrowsCount")
  private suspend fun validateBindings(command: CreateIssueTypeConfigCommand) {
    if (command.statuses.count { it.isInitial } != 1) {
      throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_CONFIG_INITIAL_STATUS_REQUIRED)
    }
    if (command.statuses.map { it.statusApiId }.distinct().size != command.statuses.size) {
      throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_CONFIG_DUPLICATE_STATUS)
    }
    if (command.properties.map { it.propertyApiId }.distinct().size != command.properties.size) {
      throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_CONFIG_DUPLICATE_PROPERTY)
    }
    catalog.findIssueType(command.tenantId, command.issueTypeApiId, command.projectId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_TYPE_NOT_FOUND)
    workflows.findWorkflow(command.tenantId, command.workflowApiId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORKFLOW_NOT_FOUND)
    command.statuses.forEach {
      catalog.findStatus(command.tenantId, it.statusApiId)
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_STATUS_NOT_FOUND)
    }
    command.properties.forEach {
      catalog.findProperty(command.tenantId, it.propertyApiId)
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_PROPERTY_NOT_FOUND)
    }
  }

  private suspend fun validateWorkflowTransitions(details: IssueTypeConfigDetails) {
    val statusIds = details.statuses.map { it.statusId }.toSet()
    val transitions = workflows.listTransitions(details.config.tenantId, details.config.workflowId)
    val invalid = transitions.firstOrNull { transition ->
      transition.fromStatusId !in statusIds || transition.toStatusId !in statusIds
    }
    if (invalid != null) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORKFLOW_TRANSITION_STATUS_UNAVAILABLE,
        "Transition ${invalid.apiId.value} references a status outside the type config.",
      )
    }
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
