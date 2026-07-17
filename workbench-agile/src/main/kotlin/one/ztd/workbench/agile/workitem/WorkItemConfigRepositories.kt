package one.ztd.workbench.agile.workitem

import java.time.OffsetDateTime
import java.util.UUID
import one.ztd.workbench.agile.workitem.model.CreateIssueStatusCommand
import one.ztd.workbench.agile.workitem.model.CreateIssueSubtypeConstraintCommand
import one.ztd.workbench.agile.workitem.model.CreateIssueTypeCommand
import one.ztd.workbench.agile.workitem.model.CreateIssueTypeConfigCommand
import one.ztd.workbench.agile.workitem.model.CreatePropertyDefinitionCommand
import one.ztd.workbench.agile.workitem.model.CreateWorkflowCommand
import one.ztd.workbench.agile.workitem.model.CreateWorkflowTransitionCommand
import one.ztd.workbench.agile.workitem.model.EffectiveIssueTypeConfig
import one.ztd.workbench.agile.workitem.model.IssueStatusRecord
import one.ztd.workbench.agile.workitem.model.IssueSubtypeConstraintRecord
import one.ztd.workbench.agile.workitem.model.IssueTypeConfigDetails
import one.ztd.workbench.agile.workitem.model.IssueTypeRecord
import one.ztd.workbench.agile.workitem.model.PropertyDefinitionRecord
import one.ztd.workbench.agile.workitem.model.WorkflowRecord
import one.ztd.workbench.agile.workitem.model.WorkflowTransitionRecord

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
