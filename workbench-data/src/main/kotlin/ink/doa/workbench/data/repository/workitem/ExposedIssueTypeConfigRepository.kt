package ink.doa.workbench.data.repository.workitem

import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.IssueTypeConfigRepository
import ink.doa.workbench.core.workitem.WorkItemCatalogRepository
import ink.doa.workbench.core.workitem.WorkflowConfigurationRepository
import ink.doa.workbench.core.workitem.model.CreateIssueTypeConfigCommand
import ink.doa.workbench.core.workitem.model.EffectiveIssueTypeConfig
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.WorkItemConfigScope
import ink.doa.workbench.data.persistence.postgres.workitem.ExposedWorkItemConfigQueries.closeCurrentConfig
import ink.doa.workbench.data.persistence.postgres.workitem.ExposedWorkItemConfigQueries.findActiveConfigRow
import ink.doa.workbench.data.persistence.postgres.workitem.ExposedWorkItemConfigQueries.findIssueTypeRow
import ink.doa.workbench.data.persistence.postgres.workitem.ExposedWorkItemConfigQueries.nextConfigVersion
import ink.doa.workbench.data.persistence.postgres.workitem.ExposedWorkItemConfigQueries.requireConfigDetails
import ink.doa.workbench.data.persistence.postgres.workitem.IssueTypeConfigPropertiesTable
import ink.doa.workbench.data.persistence.postgres.workitem.IssueTypeConfigStatusesTable
import ink.doa.workbench.data.persistence.postgres.workitem.IssueTypeConfigsTable
import ink.doa.workbench.data.persistence.postgres.workitem.now
import ink.doa.workbench.data.persistence.postgres.workitem.toIssueTypeRecord
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.springframework.stereotype.Repository

@Repository
class ExposedIssueTypeConfigRepository(
  private val database: Database,
  private val catalog: WorkItemCatalogRepository,
  private val workflows: WorkflowConfigurationRepository,
) : IssueTypeConfigRepository {
  override suspend fun createConfig(command: CreateIssueTypeConfigCommand): IssueTypeConfigDetails =
    suspendTransaction(db = database) {
      val issueType =
        catalog.findIssueType(command.tenantId, command.issueTypeApiId, command.projectId)
          ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_TYPE_NOT_FOUND)
      val workflow =
        workflows.findWorkflow(command.tenantId, command.workflowApiId)
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
      insertConfigStatuses(command, id)
      insertConfigProperties(command, id)
      requireConfigDetails(id)
    }

  private suspend fun insertConfigStatuses(command: CreateIssueTypeConfigCommand, configId: UUID) {
    command.statuses.forEach { status ->
      val record =
        catalog.findStatus(command.tenantId, status.statusApiId)
          ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_STATUS_NOT_FOUND)
      IssueTypeConfigStatusesTable.insert {
        it[IssueTypeConfigStatusesTable.id] = UUID.randomUUID().toKotlinUuid()
        it[IssueTypeConfigStatusesTable.tenantId] = command.tenantId.toKotlinUuid()
        it[IssueTypeConfigStatusesTable.issueTypeConfigId] = configId.toKotlinUuid()
        it[IssueTypeConfigStatusesTable.statusId] = record.id.toKotlinUuid()
        it[IssueTypeConfigStatusesTable.isInitial] = status.isInitial
        it[IssueTypeConfigStatusesTable.isTerminal] = status.isTerminal
        it[IssueTypeConfigStatusesTable.rank] = status.rank
      }
    }
  }

  private suspend fun insertConfigProperties(
    command: CreateIssueTypeConfigCommand,
    configId: UUID,
  ) {
    command.properties.forEach { property ->
      val record =
        catalog.findProperty(command.tenantId, property.propertyApiId)
          ?: throw ResourceNotFoundException(
            WorkbenchErrorCode.RESOURCE_WORK_ITEM_PROPERTY_NOT_FOUND
          )
      IssueTypeConfigPropertiesTable.insert {
        it[IssueTypeConfigPropertiesTable.id] = UUID.randomUUID().toKotlinUuid()
        it[IssueTypeConfigPropertiesTable.tenantId] = command.tenantId.toKotlinUuid()
        it[IssueTypeConfigPropertiesTable.issueTypeConfigId] = configId.toKotlinUuid()
        it[IssueTypeConfigPropertiesTable.propertyId] = record.id.toKotlinUuid()
        it[IssueTypeConfigPropertiesTable.validationOverride] = property.validationOverride
        it[IssueTypeConfigPropertiesTable.rank] = property.rank
        it[IssueTypeConfigPropertiesTable.displayConfig] = property.displayConfig
      }
    }
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
}
