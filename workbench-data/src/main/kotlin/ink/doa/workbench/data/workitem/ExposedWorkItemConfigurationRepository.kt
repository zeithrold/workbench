package ink.doa.workbench.data.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
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
import ink.doa.workbench.core.workitem.model.IssueTypeConfigPropertyRecord
import ink.doa.workbench.core.workitem.model.IssueTypeConfigRecord
import ink.doa.workbench.core.workitem.model.IssueTypeConfigStatusRecord
import ink.doa.workbench.core.workitem.model.IssueTypeRecord
import ink.doa.workbench.core.workitem.model.PropertyDefinitionRecord
import ink.doa.workbench.core.workitem.model.WorkItemConfigScope
import ink.doa.workbench.core.workitem.model.WorkItemPropertyDataType
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
import ink.doa.workbench.core.workitem.model.WorkflowRecord
import ink.doa.workbench.core.workitem.model.WorkflowTransitionRecord
import ink.doa.workbench.data.persistence.IssueStatusesTable
import ink.doa.workbench.data.persistence.IssueTypeConfigPropertiesTable
import ink.doa.workbench.data.persistence.IssueTypeConfigStatusesTable
import ink.doa.workbench.data.persistence.IssueTypeConfigsTable
import ink.doa.workbench.data.persistence.IssueTypesTable
import ink.doa.workbench.data.persistence.PropertyDefinitionsTable
import ink.doa.workbench.data.persistence.WorkflowTransitionsTable
import ink.doa.workbench.data.persistence.WorkflowsTable
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

@Repository
@Suppress("TooManyFunctions", "LargeClass", "LongMethod")
class ExposedWorkItemConfigurationRepository(private val database: Database) :
  WorkItemCatalogRepository, WorkflowConfigurationRepository, IssueTypeConfigRepository {

  override suspend fun createStatus(command: CreateIssueStatusCommand): IssueStatusRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val now = now()
      IssueStatusesTable.insert {
        it[IssueStatusesTable.id] = id.toKotlinUuid()
        it[IssueStatusesTable.apiId] = PublicId.new("sts").value
        it[IssueStatusesTable.tenantId] = command.tenantId.toKotlinUuid()
        it[IssueStatusesTable.code] = command.code
        it[IssueStatusesTable.name] = command.name
        it[IssueStatusesTable.statusGroup] = command.statusGroup.dbValue
        it[IssueStatusesTable.rank] = command.rank
        it[IssueStatusesTable.color] = command.color
        it[IssueStatusesTable.isTerminal] = command.isTerminal
        it[IssueStatusesTable.isActive] = true
        it[IssueStatusesTable.createdAt] = now
        it[IssueStatusesTable.updatedAt] = now
      }
      IssueStatusesTable.selectAll()
        .where { IssueStatusesTable.id eq id.toKotlinUuid() }
        .single()
        .toIssueStatusRecord()
    }

  override suspend fun listStatuses(tenantId: UUID): List<IssueStatusRecord> =
    suspendTransaction(db = database) {
      IssueStatusesTable.selectAll()
        .where {
          (IssueStatusesTable.tenantId eq tenantId.toKotlinUuid()) and
            (IssueStatusesTable.isActive eq true)
        }
        .orderBy(IssueStatusesTable.rank to SortOrder.ASC)
        .map { it.toIssueStatusRecord() }
    }

  override suspend fun findStatus(tenantId: UUID, apiIdOrCode: String): IssueStatusRecord? =
    suspendTransaction(db = database) {
      IssueStatusesTable.selectAll()
        .where {
          (IssueStatusesTable.tenantId eq tenantId.toKotlinUuid()) and
            ((IssueStatusesTable.apiId eq apiIdOrCode) or
              (IssueStatusesTable.code eq apiIdOrCode)) and
            (IssueStatusesTable.isActive eq true)
        }
        .singleOrNull()
        ?.toIssueStatusRecord()
    }

  override suspend fun deactivateStatus(
    tenantId: UUID,
    apiIdOrCode: String,
    actorUserId: UUID,
  ): IssueStatusRecord =
    suspendTransaction(db = database) {
      val row =
        IssueStatusesTable.selectAll()
          .where {
            (IssueStatusesTable.tenantId eq tenantId.toKotlinUuid()) and
              ((IssueStatusesTable.apiId eq apiIdOrCode) or
                (IssueStatusesTable.code eq apiIdOrCode)) and
              (IssueStatusesTable.isActive eq true)
          }
          .singleOrNull()
          ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_STATUS_NOT_FOUND)
      rejectIfActiveConfigsUseStatus(tenantId, row[IssueStatusesTable.id].toJavaUuid())
      val now = now()
      IssueStatusesTable.update({ IssueStatusesTable.id eq row[IssueStatusesTable.id] }) {
        it[IssueStatusesTable.isActive] = false
        it[IssueStatusesTable.updatedAt] = now
      }
      IssueStatusesTable.selectAll()
        .where { IssueStatusesTable.id eq row[IssueStatusesTable.id] }
        .single()
        .toIssueStatusRecord()
    }

  override suspend fun createProperty(
    command: CreatePropertyDefinitionCommand
  ): PropertyDefinitionRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val now = now()
      PropertyDefinitionsTable.insert {
        it[PropertyDefinitionsTable.id] = id.toKotlinUuid()
        it[PropertyDefinitionsTable.apiId] = PublicId.new("fld").value
        it[PropertyDefinitionsTable.tenantId] = command.tenantId.toKotlinUuid()
        it[PropertyDefinitionsTable.code] = command.code
        it[PropertyDefinitionsTable.name] = command.name
        it[PropertyDefinitionsTable.description] = command.description
        it[PropertyDefinitionsTable.dataType] = command.dataType.dbValue
        it[PropertyDefinitionsTable.isSystem] = false
        it[PropertyDefinitionsTable.isArray] = command.isArray
        it[PropertyDefinitionsTable.validationSchema] = command.validationSchema
        it[PropertyDefinitionsTable.searchConfig] = command.searchConfig
        it[PropertyDefinitionsTable.isActive] = true
        it[PropertyDefinitionsTable.createdAt] = now
        it[PropertyDefinitionsTable.updatedAt] = now
      }
      PropertyDefinitionsTable.selectAll()
        .where { PropertyDefinitionsTable.id eq id.toKotlinUuid() }
        .single()
        .toPropertyDefinitionRecord()
    }

  override suspend fun listProperties(tenantId: UUID): List<PropertyDefinitionRecord> =
    suspendTransaction(db = database) {
      PropertyDefinitionsTable.selectAll()
        .where {
          (PropertyDefinitionsTable.tenantId eq tenantId.toKotlinUuid()) and
            (PropertyDefinitionsTable.isActive eq true)
        }
        .orderBy(PropertyDefinitionsTable.code to SortOrder.ASC)
        .map { it.toPropertyDefinitionRecord() }
    }

  override suspend fun findProperty(
    tenantId: UUID,
    apiIdOrCode: String,
  ): PropertyDefinitionRecord? =
    suspendTransaction(db = database) {
      PropertyDefinitionsTable.selectAll()
        .where {
          (PropertyDefinitionsTable.tenantId eq tenantId.toKotlinUuid()) and
            ((PropertyDefinitionsTable.apiId eq apiIdOrCode) or
              (PropertyDefinitionsTable.code eq apiIdOrCode)) and
            (PropertyDefinitionsTable.isActive eq true)
        }
        .singleOrNull()
        ?.toPropertyDefinitionRecord()
    }

  override suspend fun deactivateProperty(
    tenantId: UUID,
    apiIdOrCode: String,
    actorUserId: UUID,
  ): PropertyDefinitionRecord =
    suspendTransaction(db = database) {
      val row =
        PropertyDefinitionsTable.selectAll()
          .where {
            (PropertyDefinitionsTable.tenantId eq tenantId.toKotlinUuid()) and
              ((PropertyDefinitionsTable.apiId eq apiIdOrCode) or
                (PropertyDefinitionsTable.code eq apiIdOrCode)) and
              (PropertyDefinitionsTable.isActive eq true)
          }
          .singleOrNull()
          ?: throw ResourceNotFoundException(
            WorkbenchErrorCode.RESOURCE_WORK_ITEM_PROPERTY_NOT_FOUND
          )
      rejectIfActiveConfigsUseProperty(tenantId, row[PropertyDefinitionsTable.id].toJavaUuid())
      val now = now()
      PropertyDefinitionsTable.update({
        PropertyDefinitionsTable.id eq row[PropertyDefinitionsTable.id]
      }) {
        it[PropertyDefinitionsTable.isActive] = false
        it[PropertyDefinitionsTable.updatedAt] = now
      }
      PropertyDefinitionsTable.selectAll()
        .where { PropertyDefinitionsTable.id eq row[PropertyDefinitionsTable.id] }
        .single()
        .toPropertyDefinitionRecord()
    }

  override suspend fun createIssueType(command: CreateIssueTypeCommand): IssueTypeRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val now = now()
      IssueTypesTable.insert {
        it[IssueTypesTable.id] = id.toKotlinUuid()
        it[IssueTypesTable.apiId] = PublicId.new("typ").value
        it[IssueTypesTable.tenantId] = command.tenantId.toKotlinUuid()
        it[IssueTypesTable.projectId] = command.projectId?.toKotlinUuid()
        it[IssueTypesTable.scope] = command.scope.dbValue
        it[IssueTypesTable.code] = command.code
        it[IssueTypesTable.name] = command.name
        it[IssueTypesTable.description] = command.description
        it[IssueTypesTable.icon] = command.icon
        it[IssueTypesTable.color] = command.color
        it[IssueTypesTable.rank] = command.rank
        it[IssueTypesTable.isActive] = true
        it[IssueTypesTable.createdAt] = now
        it[IssueTypesTable.updatedAt] = now
      }
      IssueTypesTable.selectAll()
        .where { IssueTypesTable.id eq id.toKotlinUuid() }
        .single()
        .toIssueTypeRecord()
    }

  override suspend fun listIssueTypes(tenantId: UUID, projectId: UUID?): List<IssueTypeRecord> =
    suspendTransaction(db = database) {
      val tenantUuid = tenantId.toKotlinUuid()
      val condition =
        if (projectId == null) {
          (IssueTypesTable.tenantId eq tenantUuid) and IssueTypesTable.deletedAt.isNull()
        } else {
          (IssueTypesTable.tenantId eq tenantUuid) and
            (IssueTypesTable.deletedAt.isNull()) and
            (IssueTypesTable.projectId.isNull() or
              (IssueTypesTable.projectId eq projectId.toKotlinUuid()))
        }
      IssueTypesTable.selectAll()
        .where { condition and (IssueTypesTable.isActive eq true) }
        .orderBy(IssueTypesTable.rank to SortOrder.ASC)
        .map { it.toIssueTypeRecord() }
    }

  override suspend fun findIssueType(
    tenantId: UUID,
    apiIdOrCode: String,
    projectId: UUID?,
  ): IssueTypeRecord? =
    suspendTransaction(db = database) {
      findIssueTypeRow(tenantId, apiIdOrCode, projectId)?.toIssueTypeRecord()
    }

  override suspend fun deactivateIssueType(
    tenantId: UUID,
    apiIdOrCode: String,
    actorUserId: UUID,
    projectId: UUID?,
  ): IssueTypeRecord =
    suspendTransaction(db = database) {
      val row =
        findIssueTypeRow(tenantId, apiIdOrCode, projectId)
          ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_TYPE_NOT_FOUND)
      rejectIfActiveConfigsUseIssueType(tenantId, row[IssueTypesTable.id].toJavaUuid())
      val now = now()
      IssueTypesTable.update({ IssueTypesTable.id eq row[IssueTypesTable.id] }) {
        it[IssueTypesTable.isActive] = false
        it[IssueTypesTable.archivedAt] = now
        it[IssueTypesTable.archivedBy] = actorUserId.toKotlinUuid()
        it[IssueTypesTable.updatedAt] = now
      }
      IssueTypesTable.selectAll()
        .where { IssueTypesTable.id eq row[IssueTypesTable.id] }
        .single()
        .toIssueTypeRecord()
    }

  override suspend fun createWorkflow(command: CreateWorkflowCommand): WorkflowRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val now = now()
      WorkflowsTable.insert {
        it[WorkflowsTable.id] = id.toKotlinUuid()
        it[WorkflowsTable.apiId] = PublicId.new("wfl").value
        it[WorkflowsTable.tenantId] = command.tenantId.toKotlinUuid()
        it[WorkflowsTable.code] = command.code
        it[WorkflowsTable.name] = command.name
        it[WorkflowsTable.description] = command.description
        it[WorkflowsTable.version] = nextWorkflowVersion(command.tenantId, command.code)
        it[WorkflowsTable.isActive] = true
        it[WorkflowsTable.createdBy] = command.createdBy?.toKotlinUuid()
        it[WorkflowsTable.createdAt] = now
        it[WorkflowsTable.updatedAt] = now
      }
      WorkflowsTable.selectAll()
        .where { WorkflowsTable.id eq id.toKotlinUuid() }
        .single()
        .toWorkflowRecord()
    }

  override suspend fun listWorkflows(tenantId: UUID): List<WorkflowRecord> =
    suspendTransaction(db = database) {
      WorkflowsTable.selectAll()
        .where {
          (WorkflowsTable.tenantId eq tenantId.toKotlinUuid()) and
            (WorkflowsTable.isActive eq true) and
            WorkflowsTable.deletedAt.isNull()
        }
        .orderBy(WorkflowsTable.code to SortOrder.ASC, WorkflowsTable.version to SortOrder.DESC)
        .map { it.toWorkflowRecord() }
    }

  override suspend fun findWorkflow(tenantId: UUID, apiIdOrCode: String): WorkflowRecord? =
    suspendTransaction(db = database) {
      WorkflowsTable.selectAll()
        .where {
          (WorkflowsTable.tenantId eq tenantId.toKotlinUuid()) and
            ((WorkflowsTable.apiId eq apiIdOrCode) or (WorkflowsTable.code eq apiIdOrCode)) and
            (WorkflowsTable.isActive eq true) and
            WorkflowsTable.deletedAt.isNull()
        }
        .orderBy(WorkflowsTable.version to SortOrder.DESC)
        .limit(1)
        .singleOrNull()
        ?.toWorkflowRecord()
    }

  override suspend fun deactivateWorkflow(
    tenantId: UUID,
    apiIdOrCode: String,
    actorUserId: UUID,
  ): WorkflowRecord =
    suspendTransaction(db = database) {
      val row =
        WorkflowsTable.selectAll()
          .where {
            (WorkflowsTable.tenantId eq tenantId.toKotlinUuid()) and
              ((WorkflowsTable.apiId eq apiIdOrCode) or (WorkflowsTable.code eq apiIdOrCode)) and
              (WorkflowsTable.isActive eq true) and
              WorkflowsTable.deletedAt.isNull()
          }
          .orderBy(WorkflowsTable.version to SortOrder.DESC)
          .limit(1)
          .singleOrNull()
          ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORKFLOW_NOT_FOUND)
      rejectIfActiveConfigsUseWorkflow(tenantId, row[WorkflowsTable.id].toJavaUuid())
      val now = now()
      WorkflowsTable.update({ WorkflowsTable.id eq row[WorkflowsTable.id] }) {
        it[WorkflowsTable.isActive] = false
        it[WorkflowsTable.archivedAt] = now
        it[WorkflowsTable.archivedBy] = actorUserId.toKotlinUuid()
        it[WorkflowsTable.updatedAt] = now
      }
      WorkflowsTable.selectAll()
        .where { WorkflowsTable.id eq row[WorkflowsTable.id] }
        .single()
        .toWorkflowRecord()
    }

  override suspend fun publishWorkflow(
    tenantId: UUID,
    workflowId: UUID,
    publishedAt: OffsetDateTime,
  ): WorkflowRecord =
    suspendTransaction(db = database) {
      val updated =
        WorkflowsTable.update({
          (WorkflowsTable.tenantId eq tenantId.toKotlinUuid()) and
            (WorkflowsTable.id eq workflowId.toKotlinUuid())
        }) {
          it[WorkflowsTable.publishedAt] = publishedAt
          it[WorkflowsTable.updatedAt] = publishedAt
        }
      if (updated == 0) {
        throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORKFLOW_NOT_FOUND)
      }
      WorkflowsTable.selectAll()
        .where { WorkflowsTable.id eq workflowId.toKotlinUuid() }
        .single()
        .toWorkflowRecord()
    }

  override suspend fun createTransition(
    command: CreateWorkflowTransitionCommand
  ): WorkflowTransitionRecord =
    suspendTransaction(db = database) {
      val workflow =
        findWorkflow(command.tenantId, command.workflowApiId)
          ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORKFLOW_NOT_FOUND)
      val fromStatusId =
        command.fromStatusApiId?.let { fromStatusApiId ->
          findStatus(command.tenantId, fromStatusApiId)
            ?: throw ResourceNotFoundException(
              WorkbenchErrorCode.RESOURCE_WORK_ITEM_STATUS_NOT_FOUND
            )
        }
      val toStatus =
        findStatus(command.tenantId, command.toStatusApiId)
          ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_STATUS_NOT_FOUND)
      val id = UUID.randomUUID()
      val now = now()
      WorkflowTransitionsTable.insert {
        it[WorkflowTransitionsTable.id] = id.toKotlinUuid()
        it[WorkflowTransitionsTable.apiId] = PublicId.new("trn").value
        it[WorkflowTransitionsTable.tenantId] = command.tenantId.toKotlinUuid()
        it[WorkflowTransitionsTable.workflowId] = workflow.id.toKotlinUuid()
        it[WorkflowTransitionsTable.name] = command.name
        it[WorkflowTransitionsTable.fromStatusId] = fromStatusId?.id?.toKotlinUuid()
        it[WorkflowTransitionsTable.toStatusId] = toStatus.id.toKotlinUuid()
        it[WorkflowTransitionsTable.rank] = command.rank
        it[WorkflowTransitionsTable.permissionCondition] = command.permissionCondition
        it[WorkflowTransitionsTable.preconditionAst] = command.preconditionAst
        it[WorkflowTransitionsTable.transitionFields] = command.fields
        it[WorkflowTransitionsTable.isActive] = true
        it[WorkflowTransitionsTable.createdAt] = now
        it[WorkflowTransitionsTable.updatedAt] = now
      }
      requireTransition(id)
    }

  override suspend fun listTransitions(
    tenantId: UUID,
    workflowId: UUID,
  ): List<WorkflowTransitionRecord> =
    suspendTransaction(db = database) {
      WorkflowTransitionsTable.selectAll()
        .where {
          (WorkflowTransitionsTable.tenantId eq tenantId.toKotlinUuid()) and
            (WorkflowTransitionsTable.workflowId eq workflowId.toKotlinUuid()) and
            (WorkflowTransitionsTable.isActive eq true)
        }
        .orderBy(WorkflowTransitionsTable.rank to SortOrder.ASC)
        .map { it.toWorkflowTransitionRecord() }
    }

  override suspend fun findTransition(tenantId: UUID, apiId: String): WorkflowTransitionRecord? =
    suspendTransaction(db = database) {
      WorkflowTransitionsTable.selectAll()
        .where {
          (WorkflowTransitionsTable.tenantId eq tenantId.toKotlinUuid()) and
            (WorkflowTransitionsTable.apiId eq apiId) and
            (WorkflowTransitionsTable.isActive eq true)
        }
        .singleOrNull()
        ?.toWorkflowTransitionRecord()
    }

  override suspend fun deactivateTransition(
    tenantId: UUID,
    transitionApiId: String,
  ): WorkflowTransitionRecord =
    suspendTransaction(db = database) {
      val row =
        WorkflowTransitionsTable.selectAll()
          .where {
            (WorkflowTransitionsTable.tenantId eq tenantId.toKotlinUuid()) and
              (WorkflowTransitionsTable.apiId eq transitionApiId) and
              (WorkflowTransitionsTable.isActive eq true)
          }
          .singleOrNull()
          ?: throw ResourceNotFoundException(
            WorkbenchErrorCode.RESOURCE_WORKFLOW_TRANSITION_NOT_FOUND
          )
      val now = now()
      WorkflowTransitionsTable.update({
        WorkflowTransitionsTable.id eq row[WorkflowTransitionsTable.id]
      }) {
        it[WorkflowTransitionsTable.isActive] = false
        it[WorkflowTransitionsTable.updatedAt] = now
      }
      WorkflowTransitionsTable.selectAll()
        .where { WorkflowTransitionsTable.id eq row[WorkflowTransitionsTable.id] }
        .single()
        .toWorkflowTransitionRecord()
    }

  override suspend fun createConfig(command: CreateIssueTypeConfigCommand): IssueTypeConfigDetails =
    suspendTransaction(db = database) {
      val issueType =
        findIssueType(command.tenantId, command.issueTypeApiId, command.projectId)
          ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_TYPE_NOT_FOUND)
      val workflow =
        findWorkflow(command.tenantId, command.workflowApiId)
          ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORKFLOW_NOT_FOUND)
      val now = now()
      closeCurrentConfig(command.tenantId, command.scope, command.projectId, issueType.id, now)
      val id = UUID.randomUUID()
      IssueTypeConfigsTable.insert {
        it[IssueTypeConfigsTable.id] = id.toKotlinUuid()
        it[IssueTypeConfigsTable.apiId] = PublicId.new("itc").value
        it[IssueTypeConfigsTable.tenantId] = command.tenantId.toKotlinUuid()
        it[IssueTypeConfigsTable.scope] = command.scope.dbValue
        it[IssueTypeConfigsTable.projectId] = command.projectId?.toKotlinUuid()
        it[IssueTypeConfigsTable.issueTypeId] = issueType.id.toKotlinUuid()
        it[IssueTypeConfigsTable.workflowId] = workflow.id.toKotlinUuid()
        it[IssueTypeConfigsTable.version] = nextConfigVersion(command, issueType.id)
        it[IssueTypeConfigsTable.nameOverride] = command.nameOverride
        it[IssueTypeConfigsTable.iconOverride] = command.iconOverride
        it[IssueTypeConfigsTable.colorOverride] = command.colorOverride
        it[IssueTypeConfigsTable.rank] = command.rank
        it[IssueTypeConfigsTable.isActive] = true
        it[IssueTypeConfigsTable.validFrom] = now
        it[IssueTypeConfigsTable.validTo] = null
        it[IssueTypeConfigsTable.createdBy] = command.createdBy?.toKotlinUuid()
        it[IssueTypeConfigsTable.createdAt] = now
        it[IssueTypeConfigsTable.updatedAt] = now
        it[IssueTypeConfigsTable.createFields] = command.createFields
      }
      command.statuses.forEach { status ->
        val record =
          findStatus(command.tenantId, status.statusApiId)
            ?: throw ResourceNotFoundException(
              WorkbenchErrorCode.RESOURCE_WORK_ITEM_STATUS_NOT_FOUND
            )
        IssueTypeConfigStatusesTable.insert {
          it[IssueTypeConfigStatusesTable.id] = UUID.randomUUID().toKotlinUuid()
          it[IssueTypeConfigStatusesTable.tenantId] = command.tenantId.toKotlinUuid()
          it[IssueTypeConfigStatusesTable.issueTypeConfigId] = id.toKotlinUuid()
          it[IssueTypeConfigStatusesTable.statusId] = record.id.toKotlinUuid()
          it[IssueTypeConfigStatusesTable.isInitial] = status.isInitial
          it[IssueTypeConfigStatusesTable.isTerminal] = status.isTerminal
          it[IssueTypeConfigStatusesTable.rank] = status.rank
        }
      }
      command.properties.forEach { property ->
        val record =
          findProperty(command.tenantId, property.propertyApiId)
            ?: throw ResourceNotFoundException(
              WorkbenchErrorCode.RESOURCE_WORK_ITEM_PROPERTY_NOT_FOUND
            )
        IssueTypeConfigPropertiesTable.insert {
          it[IssueTypeConfigPropertiesTable.id] = UUID.randomUUID().toKotlinUuid()
          it[IssueTypeConfigPropertiesTable.tenantId] = command.tenantId.toKotlinUuid()
          it[IssueTypeConfigPropertiesTable.issueTypeConfigId] = id.toKotlinUuid()
          it[IssueTypeConfigPropertiesTable.propertyId] = record.id.toKotlinUuid()
          it[IssueTypeConfigPropertiesTable.validationOverride] = property.validationOverride
          it[IssueTypeConfigPropertiesTable.rank] = property.rank
          it[IssueTypeConfigPropertiesTable.displayConfig] = property.displayConfig
        }
      }
      requireConfigDetails(id)
    }

  override suspend fun listConfigs(tenantId: UUID, projectId: UUID?): List<IssueTypeConfigDetails> =
    suspendTransaction(db = database) {
      val condition =
        if (projectId == null) {
          IssueTypeConfigsTable.tenantId eq tenantId.toKotlinUuid()
        } else {
          (IssueTypeConfigsTable.tenantId eq tenantId.toKotlinUuid()) and
            (IssueTypeConfigsTable.projectId.isNull() or
              (IssueTypeConfigsTable.projectId eq projectId.toKotlinUuid()))
        }
      IssueTypeConfigsTable.selectAll()
        .where { condition }
        .orderBy(IssueTypeConfigsTable.rank to SortOrder.ASC)
        .map { requireConfigDetails(it[IssueTypeConfigsTable.id].toJavaUuid()) }
    }

  override suspend fun findConfig(tenantId: UUID, apiId: String): IssueTypeConfigDetails? =
    suspendTransaction(db = database) {
      IssueTypeConfigsTable.selectAll()
        .where {
          (IssueTypeConfigsTable.tenantId eq tenantId.toKotlinUuid()) and
            (IssueTypeConfigsTable.apiId eq apiId)
        }
        .singleOrNull()
        ?.let { requireConfigDetails(it[IssueTypeConfigsTable.id].toJavaUuid()) }
    }

  override suspend fun resolveEffective(
    tenantId: UUID,
    projectId: UUID,
    issueTypeApiIdOrCode: String,
  ): EffectiveIssueTypeConfig? =
    suspendTransaction(db = database) {
      val issueType =
        findIssueTypeRow(tenantId, issueTypeApiIdOrCode, projectId)?.toIssueTypeRecord()
          ?: return@suspendTransaction null
      val projectConfig =
        findActiveConfigRow(tenantId, issueType.id, WorkItemConfigScope.PROJECT, projectId)
      if (projectConfig != null) {
        return@suspendTransaction EffectiveIssueTypeConfig(
          config = requireConfigDetails(projectConfig[IssueTypeConfigsTable.id].toJavaUuid()),
          resolvedFrom = WorkItemConfigScope.PROJECT,
        )
      }
      val tenantConfig =
        findActiveConfigRow(tenantId, issueType.id, WorkItemConfigScope.TENANT, null)
          ?: return@suspendTransaction null
      EffectiveIssueTypeConfig(
        config = requireConfigDetails(tenantConfig[IssueTypeConfigsTable.id].toJavaUuid()),
        resolvedFrom = WorkItemConfigScope.TENANT,
      )
    }

  private fun findIssueTypeRow(
    tenantId: UUID,
    apiIdOrCode: String,
    projectId: UUID?,
  ): ResultRow? {
    var condition =
      (IssueTypesTable.tenantId eq tenantId.toKotlinUuid()) and
        ((IssueTypesTable.apiId eq apiIdOrCode) or (IssueTypesTable.code eq apiIdOrCode)) and
        (IssueTypesTable.isActive eq true) and
        IssueTypesTable.deletedAt.isNull()
    if (projectId != null) {
      condition =
        condition and
          (IssueTypesTable.projectId.isNull() or
            (IssueTypesTable.projectId eq projectId.toKotlinUuid()))
    }
    return IssueTypesTable.selectAll()
      .where { condition }
      .orderBy(IssueTypesTable.projectId to SortOrder.DESC_NULLS_LAST)
      .limit(1)
      .singleOrNull()
  }

  private fun findActiveConfigRow(
    tenantId: UUID,
    issueTypeId: UUID,
    scope: WorkItemConfigScope,
    projectId: UUID?,
  ): ResultRow? {
    var condition =
      (IssueTypeConfigsTable.tenantId eq tenantId.toKotlinUuid()) and
        (IssueTypeConfigsTable.issueTypeId eq issueTypeId.toKotlinUuid()) and
        (IssueTypeConfigsTable.scope eq scope.dbValue) and
        (IssueTypeConfigsTable.isActive eq true) and
        IssueTypeConfigsTable.validTo.isNull()
    condition =
      if (projectId == null) {
        condition and IssueTypeConfigsTable.projectId.isNull()
      } else {
        condition and (IssueTypeConfigsTable.projectId eq projectId.toKotlinUuid())
      }
    return IssueTypeConfigsTable.selectAll().where { condition }.singleOrNull()
  }

  private fun closeCurrentConfig(
    tenantId: UUID,
    scope: WorkItemConfigScope,
    projectId: UUID?,
    issueTypeId: UUID,
    closedAt: OffsetDateTime,
  ) {
    var condition =
      (IssueTypeConfigsTable.tenantId eq tenantId.toKotlinUuid()) and
        (IssueTypeConfigsTable.scope eq scope.dbValue) and
        (IssueTypeConfigsTable.issueTypeId eq issueTypeId.toKotlinUuid()) and
        IssueTypeConfigsTable.validTo.isNull()
    condition =
      if (projectId == null) {
        condition and IssueTypeConfigsTable.projectId.isNull()
      } else {
        condition and (IssueTypeConfigsTable.projectId eq projectId.toKotlinUuid())
      }
    IssueTypeConfigsTable.update({ condition }) {
      it[IssueTypeConfigsTable.isActive] = false
      it[IssueTypeConfigsTable.validTo] = closedAt
      it[IssueTypeConfigsTable.updatedAt] = closedAt
    }
  }

  private fun rejectIfActiveConfigsUseStatus(tenantId: UUID, statusId: UUID) {
    val inUse =
      (IssueTypeConfigStatusesTable innerJoin IssueTypeConfigsTable)
        .selectAll()
        .where {
          (IssueTypeConfigStatusesTable.tenantId eq tenantId.toKotlinUuid()) and
            (IssueTypeConfigStatusesTable.statusId eq statusId.toKotlinUuid()) and
            (IssueTypeConfigsTable.isActive eq true) and
            IssueTypeConfigsTable.validTo.isNull()
        }
        .limit(1)
        .singleOrNull() != null
    rejectIfInUse(inUse, "Status is still used by an active issue type config.")
  }

  private fun rejectIfActiveConfigsUseProperty(tenantId: UUID, propertyId: UUID) {
    val inUse =
      (IssueTypeConfigPropertiesTable innerJoin IssueTypeConfigsTable)
        .selectAll()
        .where {
          (IssueTypeConfigPropertiesTable.tenantId eq tenantId.toKotlinUuid()) and
            (IssueTypeConfigPropertiesTable.propertyId eq propertyId.toKotlinUuid()) and
            (IssueTypeConfigsTable.isActive eq true) and
            IssueTypeConfigsTable.validTo.isNull()
        }
        .limit(1)
        .singleOrNull() != null
    rejectIfInUse(inUse, "Property is still used by an active issue type config.")
  }

  private fun rejectIfActiveConfigsUseIssueType(tenantId: UUID, issueTypeId: UUID) {
    val inUse =
      IssueTypeConfigsTable.selectAll()
        .where {
          (IssueTypeConfigsTable.tenantId eq tenantId.toKotlinUuid()) and
            (IssueTypeConfigsTable.issueTypeId eq issueTypeId.toKotlinUuid()) and
            (IssueTypeConfigsTable.isActive eq true) and
            IssueTypeConfigsTable.validTo.isNull()
        }
        .limit(1)
        .singleOrNull() != null
    rejectIfInUse(inUse, "Issue type is still used by an active issue type config.")
  }

  private fun rejectIfActiveConfigsUseWorkflow(tenantId: UUID, workflowId: UUID) {
    val inUse =
      IssueTypeConfigsTable.selectAll()
        .where {
          (IssueTypeConfigsTable.tenantId eq tenantId.toKotlinUuid()) and
            (IssueTypeConfigsTable.workflowId eq workflowId.toKotlinUuid()) and
            (IssueTypeConfigsTable.isActive eq true) and
            IssueTypeConfigsTable.validTo.isNull()
        }
        .limit(1)
        .singleOrNull() != null
    rejectIfInUse(inUse, "Workflow is still used by an active issue type config.")
  }

  private fun rejectIfInUse(inUse: Boolean, message: String) {
    if (inUse) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_CONFIG_RESOURCE_IN_USE,
        message,
      )
    }
  }

  private fun nextWorkflowVersion(tenantId: UUID, code: String): Int =
    WorkflowsTable.selectAll()
      .where {
        (WorkflowsTable.tenantId eq tenantId.toKotlinUuid()) and (WorkflowsTable.code eq code)
      }
      .maxOfOrNull { it[WorkflowsTable.version] }
      ?.plus(1) ?: 1

  private fun nextConfigVersion(command: CreateIssueTypeConfigCommand, issueTypeId: UUID): Int {
    val projectId = command.projectId
    var condition =
      (IssueTypeConfigsTable.tenantId eq command.tenantId.toKotlinUuid()) and
        (IssueTypeConfigsTable.scope eq command.scope.dbValue) and
        (IssueTypeConfigsTable.issueTypeId eq issueTypeId.toKotlinUuid())
    condition =
      if (projectId == null) {
        condition and IssueTypeConfigsTable.projectId.isNull()
      } else {
        condition and (IssueTypeConfigsTable.projectId eq projectId.toKotlinUuid())
      }
    return IssueTypeConfigsTable.selectAll()
      .where { condition }
      .maxOfOrNull {
        it[IssueTypeConfigsTable.version]
      }
      ?.plus(1) ?: 1
  }

  private fun requireTransition(id: UUID): WorkflowTransitionRecord =
    WorkflowTransitionsTable.selectAll()
      .where { WorkflowTransitionsTable.id eq id.toKotlinUuid() }
      .single()
      .toWorkflowTransitionRecord()

  private fun requireConfigDetails(id: UUID): IssueTypeConfigDetails {
    val config =
      IssueTypeConfigsTable.selectAll()
        .where { IssueTypeConfigsTable.id eq id.toKotlinUuid() }
        .single()
        .toIssueTypeConfigRecord()
    val statuses =
      (IssueTypeConfigStatusesTable innerJoin IssueStatusesTable)
        .selectAll()
        .where { IssueTypeConfigStatusesTable.issueTypeConfigId eq id.toKotlinUuid() }
        .orderBy(IssueTypeConfigStatusesTable.rank to SortOrder.ASC)
        .map { it.toIssueTypeConfigStatusRecord() }
    val properties =
      (IssueTypeConfigPropertiesTable innerJoin PropertyDefinitionsTable)
        .selectAll()
        .where { IssueTypeConfigPropertiesTable.issueTypeConfigId eq id.toKotlinUuid() }
        .orderBy(IssueTypeConfigPropertiesTable.rank to SortOrder.ASC)
        .map { it.toIssueTypeConfigPropertyRecord() }
    return IssueTypeConfigDetails(config = config, statuses = statuses, properties = properties)
  }

  private fun ResultRow.toIssueStatusRecord(): IssueStatusRecord =
    IssueStatusRecord(
      id = this[IssueStatusesTable.id].toJavaUuid(),
      apiId = PublicId(this[IssueStatusesTable.apiId]),
      tenantId = this[IssueStatusesTable.tenantId].toJavaUuid(),
      code = this[IssueStatusesTable.code],
      name = this[IssueStatusesTable.name],
      statusGroup = WorkItemStatusGroup.fromDbValue(this[IssueStatusesTable.statusGroup]),
      rank = this[IssueStatusesTable.rank],
      color = this[IssueStatusesTable.color],
      isTerminal = this[IssueStatusesTable.isTerminal],
      isActive = this[IssueStatusesTable.isActive],
      createdAt = this[IssueStatusesTable.createdAt],
      updatedAt = this[IssueStatusesTable.updatedAt],
    )

  private fun ResultRow.toPropertyDefinitionRecord(): PropertyDefinitionRecord =
    PropertyDefinitionRecord(
      id = this[PropertyDefinitionsTable.id].toJavaUuid(),
      apiId = PublicId(this[PropertyDefinitionsTable.apiId]),
      tenantId = this[PropertyDefinitionsTable.tenantId].toJavaUuid(),
      code = this[PropertyDefinitionsTable.code],
      name = this[PropertyDefinitionsTable.name],
      description = this[PropertyDefinitionsTable.description],
      dataType = WorkItemPropertyDataType.fromDbValue(this[PropertyDefinitionsTable.dataType]),
      isSystem = this[PropertyDefinitionsTable.isSystem],
      isArray = this[PropertyDefinitionsTable.isArray],
      validationSchema = this[PropertyDefinitionsTable.validationSchema].asObject(),
      searchConfig = this[PropertyDefinitionsTable.searchConfig].asObject(),
      isActive = this[PropertyDefinitionsTable.isActive],
      createdAt = this[PropertyDefinitionsTable.createdAt],
      updatedAt = this[PropertyDefinitionsTable.updatedAt],
    )

  private fun ResultRow.toIssueTypeRecord(): IssueTypeRecord =
    IssueTypeRecord(
      id = this[IssueTypesTable.id].toJavaUuid(),
      apiId = PublicId(this[IssueTypesTable.apiId]),
      tenantId = this[IssueTypesTable.tenantId].toJavaUuid(),
      projectId = this[IssueTypesTable.projectId]?.toJavaUuid(),
      scope = WorkItemConfigScope.fromDbValue(this[IssueTypesTable.scope]),
      code = this[IssueTypesTable.code],
      name = this[IssueTypesTable.name],
      description = this[IssueTypesTable.description],
      icon = this[IssueTypesTable.icon],
      color = this[IssueTypesTable.color],
      rank = this[IssueTypesTable.rank],
      isActive = this[IssueTypesTable.isActive],
      createdAt = this[IssueTypesTable.createdAt],
      updatedAt = this[IssueTypesTable.updatedAt],
    )

  private fun ResultRow.toWorkflowRecord(): WorkflowRecord =
    WorkflowRecord(
      id = this[WorkflowsTable.id].toJavaUuid(),
      apiId = PublicId(this[WorkflowsTable.apiId]),
      tenantId = this[WorkflowsTable.tenantId].toJavaUuid(),
      code = this[WorkflowsTable.code],
      name = this[WorkflowsTable.name],
      description = this[WorkflowsTable.description],
      version = this[WorkflowsTable.version],
      isActive = this[WorkflowsTable.isActive],
      publishedAt = this[WorkflowsTable.publishedAt],
      createdBy = this[WorkflowsTable.createdBy]?.toJavaUuid(),
      createdAt = this[WorkflowsTable.createdAt],
      updatedAt = this[WorkflowsTable.updatedAt],
    )

  private fun ResultRow.toWorkflowTransitionRecord(): WorkflowTransitionRecord =
    WorkflowTransitionRecord(
      id = this[WorkflowTransitionsTable.id].toJavaUuid(),
      apiId = PublicId(this[WorkflowTransitionsTable.apiId]),
      tenantId = this[WorkflowTransitionsTable.tenantId].toJavaUuid(),
      workflowId = this[WorkflowTransitionsTable.workflowId].toJavaUuid(),
      name = this[WorkflowTransitionsTable.name],
      fromStatusId = this[WorkflowTransitionsTable.fromStatusId]?.toJavaUuid(),
      fromStatusApiId =
        this[WorkflowTransitionsTable.fromStatusId]?.toJavaUuid()?.let(::statusPublicId),
      toStatusId = this[WorkflowTransitionsTable.toStatusId].toJavaUuid(),
      toStatusApiId = statusPublicId(this[WorkflowTransitionsTable.toStatusId].toJavaUuid()),
      rank = this[WorkflowTransitionsTable.rank],
      permissionCondition = this[WorkflowTransitionsTable.permissionCondition].asObject(),
      preconditionAst = this[WorkflowTransitionsTable.preconditionAst].asObject(),
      fields = this[WorkflowTransitionsTable.transitionFields].asObject(),
      isActive = this[WorkflowTransitionsTable.isActive],
      createdAt = this[WorkflowTransitionsTable.createdAt],
      updatedAt = this[WorkflowTransitionsTable.updatedAt],
    )

  private fun ResultRow.toIssueTypeConfigRecord(): IssueTypeConfigRecord {
    val issueType =
      IssueTypesTable.selectAll()
        .where { IssueTypesTable.id eq this[IssueTypeConfigsTable.issueTypeId] }
        .single()
    val workflow =
      WorkflowsTable.selectAll()
        .where { WorkflowsTable.id eq this[IssueTypeConfigsTable.workflowId] }
        .single()
    return IssueTypeConfigRecord(
      id = this[IssueTypeConfigsTable.id].toJavaUuid(),
      apiId = PublicId(this[IssueTypeConfigsTable.apiId]),
      tenantId = this[IssueTypeConfigsTable.tenantId].toJavaUuid(),
      scope = WorkItemConfigScope.fromDbValue(this[IssueTypeConfigsTable.scope]),
      projectId = this[IssueTypeConfigsTable.projectId]?.toJavaUuid(),
      issueTypeId = this[IssueTypeConfigsTable.issueTypeId].toJavaUuid(),
      issueTypeApiId = PublicId(issueType[IssueTypesTable.apiId]),
      workflowId = this[IssueTypeConfigsTable.workflowId].toJavaUuid(),
      workflowApiId = PublicId(workflow[WorkflowsTable.apiId]),
      version = this[IssueTypeConfigsTable.version],
      nameOverride = this[IssueTypeConfigsTable.nameOverride],
      iconOverride = this[IssueTypeConfigsTable.iconOverride],
      colorOverride = this[IssueTypeConfigsTable.colorOverride],
      rank = this[IssueTypeConfigsTable.rank],
      isActive = this[IssueTypeConfigsTable.isActive],
      validFrom = this[IssueTypeConfigsTable.validFrom],
      validTo = this[IssueTypeConfigsTable.validTo],
      createdBy = this[IssueTypeConfigsTable.createdBy]?.toJavaUuid(),
      createdAt = this[IssueTypeConfigsTable.createdAt],
      updatedAt = this[IssueTypeConfigsTable.updatedAt],
      createFields = this[IssueTypeConfigsTable.createFields].asObject(),
    )
  }

  private fun ResultRow.toIssueTypeConfigStatusRecord(): IssueTypeConfigStatusRecord =
    IssueTypeConfigStatusRecord(
      id = this[IssueTypeConfigStatusesTable.id].toJavaUuid(),
      tenantId = this[IssueTypeConfigStatusesTable.tenantId].toJavaUuid(),
      issueTypeConfigId = this[IssueTypeConfigStatusesTable.issueTypeConfigId].toJavaUuid(),
      statusId = this[IssueTypeConfigStatusesTable.statusId].toJavaUuid(),
      statusApiId = PublicId(this[IssueStatusesTable.apiId]),
      code = this[IssueStatusesTable.code],
      name = this[IssueStatusesTable.name],
      statusGroup = WorkItemStatusGroup.fromDbValue(this[IssueStatusesTable.statusGroup]),
      isInitial = this[IssueTypeConfigStatusesTable.isInitial],
      isTerminal = this[IssueTypeConfigStatusesTable.isTerminal],
      rank = this[IssueTypeConfigStatusesTable.rank],
    )

  private fun ResultRow.toIssueTypeConfigPropertyRecord(): IssueTypeConfigPropertyRecord =
    IssueTypeConfigPropertyRecord(
      id = this[IssueTypeConfigPropertiesTable.id].toJavaUuid(),
      tenantId = this[IssueTypeConfigPropertiesTable.tenantId].toJavaUuid(),
      issueTypeConfigId = this[IssueTypeConfigPropertiesTable.issueTypeConfigId].toJavaUuid(),
      propertyId = this[IssueTypeConfigPropertiesTable.propertyId].toJavaUuid(),
      propertyApiId = PublicId(this[PropertyDefinitionsTable.apiId]),
      code = this[PropertyDefinitionsTable.code],
      name = this[PropertyDefinitionsTable.name],
      dataType = WorkItemPropertyDataType.fromDbValue(this[PropertyDefinitionsTable.dataType]),
      validationOverride = this[IssueTypeConfigPropertiesTable.validationOverride].asObject(),
      rank = this[IssueTypeConfigPropertiesTable.rank],
      displayConfig = this[IssueTypeConfigPropertiesTable.displayConfig].asObject(),
    )

  private fun statusPublicId(statusId: UUID): PublicId? =
    IssueStatusesTable.selectAll()
      .where { IssueStatusesTable.id eq statusId.toKotlinUuid() }
      .singleOrNull()
      ?.let { PublicId(it[IssueStatusesTable.apiId]) }

  private fun now(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)

  private fun JsonElement.asObject(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())
}
