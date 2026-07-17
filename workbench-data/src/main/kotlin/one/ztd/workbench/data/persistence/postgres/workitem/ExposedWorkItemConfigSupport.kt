package one.ztd.workbench.data.persistence.postgres.workitem

import java.time.OffsetDateTime
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import one.ztd.workbench.agile.workitem.model.CreateIssueTypeConfigCommand
import one.ztd.workbench.agile.workitem.model.IssueStatusRecord
import one.ztd.workbench.agile.workitem.model.IssueTypeConfigDetails
import one.ztd.workbench.agile.workitem.model.IssueTypeConfigPropertyRecord
import one.ztd.workbench.agile.workitem.model.IssueTypeConfigRecord
import one.ztd.workbench.agile.workitem.model.IssueTypeConfigStatusRecord
import one.ztd.workbench.agile.workitem.model.IssueTypeRecord
import one.ztd.workbench.agile.workitem.model.PropertyDefinitionRecord
import one.ztd.workbench.agile.workitem.model.WorkItemConfigScope
import one.ztd.workbench.agile.workitem.model.WorkItemPropertyDataType
import one.ztd.workbench.agile.workitem.model.WorkItemStatusGroup
import one.ztd.workbench.agile.workitem.model.WorkflowRecord
import one.ztd.workbench.agile.workitem.model.WorkflowTransitionRecord
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.kernel.common.ids.PublicId
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

internal fun ResultRow.toIssueStatusRecord(): IssueStatusRecord =
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

internal fun ResultRow.toPropertyDefinitionRecord(): PropertyDefinitionRecord =
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

internal fun ResultRow.toIssueTypeRecord(): IssueTypeRecord =
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

internal fun ResultRow.toWorkflowRecord(): WorkflowRecord =
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

internal fun ResultRow.toWorkflowTransitionRecord(): WorkflowTransitionRecord =
  WorkflowTransitionRecord(
    id = this[WorkflowTransitionsTable.id].toJavaUuid(),
    apiId = PublicId(this[WorkflowTransitionsTable.apiId]),
    tenantId = this[WorkflowTransitionsTable.tenantId].toJavaUuid(),
    workflowId = this[WorkflowTransitionsTable.workflowId].toJavaUuid(),
    name = this[WorkflowTransitionsTable.name],
    fromStatusId = this[WorkflowTransitionsTable.fromStatusId]?.toJavaUuid(),
    fromStatusApiId =
      this[WorkflowTransitionsTable.fromStatusId]
        ?.toJavaUuid()
        ?.let(ExposedWorkItemConfigQueries::statusPublicId),
    toStatusId = this[WorkflowTransitionsTable.toStatusId].toJavaUuid(),
    toStatusApiId =
      ExposedWorkItemConfigQueries.statusPublicId(
        this[WorkflowTransitionsTable.toStatusId].toJavaUuid()
      ),
    rank = this[WorkflowTransitionsTable.rank],
    preconditionAst = this[WorkflowTransitionsTable.preconditionAst].asObject(),
    fields = this[WorkflowTransitionsTable.transitionFields].asObject(),
    isActive = this[WorkflowTransitionsTable.isActive],
    createdAt = this[WorkflowTransitionsTable.createdAt],
    updatedAt = this[WorkflowTransitionsTable.updatedAt],
  )

internal fun ResultRow.toIssueTypeConfigRecord(): IssueTypeConfigRecord {
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

internal fun ResultRow.toIssueTypeConfigStatusRecord(): IssueTypeConfigStatusRecord =
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

internal fun ResultRow.toIssueTypeConfigPropertyRecord(): IssueTypeConfigPropertyRecord =
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

internal object ExposedWorkItemConfigQueries {
  internal fun statusPublicId(statusId: UUID): PublicId? =
    IssueStatusesTable.selectAll()
      .where { IssueStatusesTable.id eq statusId.toKotlinUuid() }
      .singleOrNull()
      ?.let { PublicId(it[IssueStatusesTable.apiId]) }

  internal fun findIssueTypeRow(
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

  internal fun findActiveConfigRow(
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

  internal fun closeCurrentConfig(
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

  internal fun nextWorkflowVersion(tenantId: UUID, code: String): Int =
    WorkflowsTable.selectAll()
      .where {
        (WorkflowsTable.tenantId eq tenantId.toKotlinUuid()) and (WorkflowsTable.code eq code)
      }
      .maxOfOrNull { it[WorkflowsTable.version] }
      ?.plus(1) ?: 1

  internal fun nextConfigVersion(command: CreateIssueTypeConfigCommand, issueTypeId: UUID): Int {
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

  internal fun requireTransition(id: UUID): WorkflowTransitionRecord =
    WorkflowTransitionsTable.selectAll()
      .where { WorkflowTransitionsTable.id eq id.toKotlinUuid() }
      .single()
      .toWorkflowTransitionRecord()

  internal fun requireConfigDetails(id: UUID): IssueTypeConfigDetails {
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
}

internal object ExposedWorkItemConfigUsageChecks {
  internal fun rejectIfActiveConfigsUseStatus(tenantId: UUID, statusId: UUID) {
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

  internal fun rejectIfActiveConfigsUseProperty(tenantId: UUID, propertyId: UUID) {
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

  internal fun rejectIfActiveConfigsUseIssueType(tenantId: UUID, issueTypeId: UUID) {
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

  internal fun rejectIfActiveConfigsUseWorkflow(tenantId: UUID, workflowId: UUID) {
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

  internal fun rejectIfInUse(inUse: Boolean, message: String) {
    if (inUse) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_CONFIG_RESOURCE_IN_USE,
        message,
      )
    }
  }
}
