package ink.doa.workbench.core.workitem

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
import ink.doa.workbench.core.workitem.model.IssueTypeRecord
import ink.doa.workbench.core.workitem.model.PropertyDefinitionRecord
import ink.doa.workbench.core.workitem.model.WorkflowRecord
import ink.doa.workbench.core.workitem.model.WorkflowTransitionRecord
import java.time.OffsetDateTime
import java.util.UUID

interface WorkItemCatalogRepository :
  IssueStatusRepository, PropertyDefinitionRepository, IssueTypeRepository

interface IssueStatusRepository {
  suspend fun createStatus(command: CreateIssueStatusCommand): IssueStatusRecord

  suspend fun listStatuses(tenantId: UUID): List<IssueStatusRecord>

  suspend fun findStatus(tenantId: UUID, apiIdOrCode: String): IssueStatusRecord?

  suspend fun deactivateStatus(
    tenantId: UUID,
    apiIdOrCode: String,
    actorUserId: UUID,
  ): IssueStatusRecord
}

interface PropertyDefinitionRepository {
  suspend fun createProperty(command: CreatePropertyDefinitionCommand): PropertyDefinitionRecord

  suspend fun listProperties(tenantId: UUID): List<PropertyDefinitionRecord>

  suspend fun findProperty(tenantId: UUID, apiIdOrCode: String): PropertyDefinitionRecord?

  suspend fun deactivateProperty(
    tenantId: UUID,
    apiIdOrCode: String,
    actorUserId: UUID,
  ): PropertyDefinitionRecord
}

interface IssueTypeRepository {
  suspend fun createIssueType(command: CreateIssueTypeCommand): IssueTypeRecord

  suspend fun listIssueTypes(tenantId: UUID, projectId: UUID? = null): List<IssueTypeRecord>

  suspend fun findIssueType(
    tenantId: UUID,
    apiIdOrCode: String,
    projectId: UUID? = null,
  ): IssueTypeRecord?

  suspend fun deactivateIssueType(
    tenantId: UUID,
    apiIdOrCode: String,
    actorUserId: UUID,
    projectId: UUID? = null,
  ): IssueTypeRecord
}

interface WorkflowConfigurationRepository {
  suspend fun createWorkflow(command: CreateWorkflowCommand): WorkflowRecord

  suspend fun listWorkflows(tenantId: UUID): List<WorkflowRecord>

  suspend fun findWorkflow(tenantId: UUID, apiIdOrCode: String): WorkflowRecord?

  suspend fun deactivateWorkflow(
    tenantId: UUID,
    apiIdOrCode: String,
    actorUserId: UUID,
  ): WorkflowRecord

  suspend fun publishWorkflow(
    tenantId: UUID,
    workflowId: UUID,
    publishedAt: OffsetDateTime,
  ): WorkflowRecord

  suspend fun createTransition(command: CreateWorkflowTransitionCommand): WorkflowTransitionRecord

  suspend fun listTransitions(tenantId: UUID, workflowId: UUID): List<WorkflowTransitionRecord>

  suspend fun findTransition(tenantId: UUID, apiId: String): WorkflowTransitionRecord?

  suspend fun deactivateTransition(
    tenantId: UUID,
    transitionApiId: String,
  ): WorkflowTransitionRecord
}

interface IssueTypeConfigRepository {
  suspend fun createConfig(command: CreateIssueTypeConfigCommand): IssueTypeConfigDetails

  suspend fun listConfigs(tenantId: UUID, projectId: UUID? = null): List<IssueTypeConfigDetails>

  suspend fun findConfig(tenantId: UUID, apiId: String): IssueTypeConfigDetails?

  suspend fun resolveEffective(
    tenantId: UUID,
    projectId: UUID,
    issueTypeApiIdOrCode: String,
  ): EffectiveIssueTypeConfig?
}

interface IssueSubtypeConstraintRepository {
  suspend fun create(command: CreateIssueSubtypeConstraintCommand): IssueSubtypeConstraintRecord

  suspend fun list(
    tenantId: UUID,
    projectId: UUID? = null,
  ): List<IssueSubtypeConstraintRecord>

  suspend fun deactivate(
    tenantId: UUID,
    constraintId: UUID,
    actorUserId: UUID,
  ): IssueSubtypeConstraintRecord

  suspend fun findAllowedChildType(
    tenantId: UUID,
    projectId: UUID,
    parentIssueTypeId: UUID,
    childIssueTypeId: UUID,
  ): IssueSubtypeConstraintRecord?

  suspend fun isChildOnlyType(
    tenantId: UUID,
    projectId: UUID,
    issueTypeId: UUID,
  ): Boolean
}
