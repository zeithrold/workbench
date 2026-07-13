package ink.doa.workbench.data.repository.workitem

import ink.doa.workbench.agile.workitem.WorkItemCatalogRepository
import ink.doa.workbench.agile.workitem.model.CreateIssueStatusCommand
import ink.doa.workbench.agile.workitem.model.CreateIssueTypeCommand
import ink.doa.workbench.agile.workitem.model.CreatePropertyDefinitionCommand
import ink.doa.workbench.agile.workitem.model.IssueStatusRecord
import ink.doa.workbench.agile.workitem.model.IssueTypeRecord
import ink.doa.workbench.agile.workitem.model.PropertyDefinitionRecord
import ink.doa.workbench.data.persistence.postgres.workitem.ExposedWorkItemConfigQueries.findIssueTypeRow
import ink.doa.workbench.data.persistence.postgres.workitem.ExposedWorkItemConfigUsageChecks.rejectIfActiveConfigsUseIssueType
import ink.doa.workbench.data.persistence.postgres.workitem.ExposedWorkItemConfigUsageChecks.rejectIfActiveConfigsUseProperty
import ink.doa.workbench.data.persistence.postgres.workitem.ExposedWorkItemConfigUsageChecks.rejectIfActiveConfigsUseStatus
import ink.doa.workbench.data.persistence.postgres.workitem.IssueStatusesTable
import ink.doa.workbench.data.persistence.postgres.workitem.IssueTypesTable
import ink.doa.workbench.data.persistence.postgres.workitem.PropertyDefinitionsTable
import ink.doa.workbench.data.persistence.postgres.workitem.now
import ink.doa.workbench.data.persistence.postgres.workitem.toIssueStatusRecord
import ink.doa.workbench.data.persistence.postgres.workitem.toIssueTypeRecord
import ink.doa.workbench.data.persistence.postgres.workitem.toPropertyDefinitionRecord
import ink.doa.workbench.kernel.common.errors.ResourceNotFoundException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import ink.doa.workbench.kernel.common.ids.PublicId
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
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

@Repository
class ExposedWorkItemCatalogRepository(private val database: Database) : WorkItemCatalogRepository {
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
}
