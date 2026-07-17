package one.ztd.workbench.data.repository.workitem

import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import one.ztd.workbench.agile.workitem.IssueSubtypeConstraintRepository
import one.ztd.workbench.agile.workitem.model.CreateIssueSubtypeConstraintCommand
import one.ztd.workbench.agile.workitem.model.IssueSubtypeConstraintRecord
import one.ztd.workbench.data.persistence.postgres.workitem.IssueSubtypeConstraintsTable
import one.ztd.workbench.data.persistence.postgres.workitem.IssueTypesTable
import one.ztd.workbench.data.persistence.postgres.workitem.now
import one.ztd.workbench.data.persistence.postgres.workitem.requirePublicId
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.kernel.common.errors.requireValid
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
class ExposedIssueSubtypeConstraintRepository(private val database: Database) :
  IssueSubtypeConstraintRepository {
  override suspend fun create(
    command: CreateIssueSubtypeConstraintCommand
  ): IssueSubtypeConstraintRecord =
    suspendTransaction(db = database) {
      requireValidCardinality(command)
      val parentType =
        findActiveIssueType(command.tenantId, command.parentIssueTypeApiId, command.projectId)
          ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_TYPE_NOT_FOUND)
      val childType =
        findActiveIssueType(command.tenantId, command.childIssueTypeApiId, command.projectId)
          ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_TYPE_NOT_FOUND)
      val id = UUID.randomUUID()
      val now = now()
      IssueSubtypeConstraintsTable.insert {
        it[IssueSubtypeConstraintsTable.id] = id.toKotlinUuid()
        it[IssueSubtypeConstraintsTable.tenantId] = command.tenantId.toKotlinUuid()
        it[IssueSubtypeConstraintsTable.projectId] = command.projectId?.toKotlinUuid()
        it[IssueSubtypeConstraintsTable.parentIssueTypeId] = parentType[IssueTypesTable.id]
        it[IssueSubtypeConstraintsTable.childIssueTypeId] = childType[IssueTypesTable.id]
        it[IssueSubtypeConstraintsTable.isDefault] = command.isDefault
        it[IssueSubtypeConstraintsTable.minChildren] = command.minChildren
        it[IssueSubtypeConstraintsTable.maxChildren] = command.maxChildren
        it[IssueSubtypeConstraintsTable.isActive] = true
        it[IssueSubtypeConstraintsTable.createdBy] = command.createdBy?.toKotlinUuid()
        it[IssueSubtypeConstraintsTable.createdAt] = now
        it[IssueSubtypeConstraintsTable.updatedAt] = now
      }
      IssueSubtypeConstraintsTable.selectAll()
        .where { IssueSubtypeConstraintsTable.id eq id.toKotlinUuid() }
        .single()
        .toIssueSubtypeConstraintRecord()
    }

  override suspend fun list(
    tenantId: UUID,
    projectId: UUID?,
  ): List<IssueSubtypeConstraintRecord> =
    suspendTransaction(db = database) {
      val tenantCondition =
        (IssueSubtypeConstraintsTable.tenantId eq tenantId.toKotlinUuid()) and
          (IssueSubtypeConstraintsTable.isActive eq true)
      val scopeCondition =
        projectId?.let {
          IssueSubtypeConstraintsTable.projectId.isNull() or
            (IssueSubtypeConstraintsTable.projectId eq it.toKotlinUuid())
        } ?: IssueSubtypeConstraintsTable.projectId.isNull()
      IssueSubtypeConstraintsTable.selectAll()
        .where { tenantCondition and scopeCondition }
        .orderBy(IssueSubtypeConstraintsTable.createdAt to SortOrder.ASC)
        .map { it.toIssueSubtypeConstraintRecord() }
    }

  override suspend fun deactivate(
    tenantId: UUID,
    constraintId: UUID,
    actorUserId: UUID,
  ): IssueSubtypeConstraintRecord =
    suspendTransaction(db = database) {
      val row =
        IssueSubtypeConstraintsTable.selectAll()
          .where {
            (IssueSubtypeConstraintsTable.tenantId eq tenantId.toKotlinUuid()) and
              (IssueSubtypeConstraintsTable.id eq constraintId.toKotlinUuid()) and
              (IssueSubtypeConstraintsTable.isActive eq true)
          }
          .singleOrNull()
          ?: throw ResourceNotFoundException(
            WorkbenchErrorCode.RESOURCE_WORK_ITEM_SUBTYPE_CONSTRAINT_NOT_FOUND
          )
      val now = now()
      IssueSubtypeConstraintsTable.update({
        IssueSubtypeConstraintsTable.id eq row[IssueSubtypeConstraintsTable.id]
      }) {
        it[IssueSubtypeConstraintsTable.isActive] = false
        it[IssueSubtypeConstraintsTable.updatedAt] = now
      }
      IssueSubtypeConstraintsTable.selectAll()
        .where { IssueSubtypeConstraintsTable.id eq row[IssueSubtypeConstraintsTable.id] }
        .single()
        .toIssueSubtypeConstraintRecord()
    }

  override suspend fun findAllowedChildType(
    tenantId: UUID,
    projectId: UUID,
    parentIssueTypeId: UUID,
    childIssueTypeId: UUID,
  ): IssueSubtypeConstraintRecord? =
    suspendTransaction(db = database) {
      findEffectiveConstraint(
          tenantId = tenantId,
          projectId = projectId,
          parentIssueTypeId = parentIssueTypeId,
          childIssueTypeId = childIssueTypeId,
        )
        ?.toIssueSubtypeConstraintRecord()
    }

  override suspend fun isChildOnlyType(
    tenantId: UUID,
    projectId: UUID,
    issueTypeId: UUID,
  ): Boolean =
    suspendTransaction(db = database) {
      IssueSubtypeConstraintsTable.selectAll()
        .where {
          (IssueSubtypeConstraintsTable.tenantId eq tenantId.toKotlinUuid()) and
            (IssueSubtypeConstraintsTable.childIssueTypeId eq issueTypeId.toKotlinUuid()) and
            (IssueSubtypeConstraintsTable.isActive eq true) and
            (IssueSubtypeConstraintsTable.projectId eq projectId.toKotlinUuid())
        }
        .limit(1)
        .any() ||
        IssueSubtypeConstraintsTable.selectAll()
          .where {
            (IssueSubtypeConstraintsTable.tenantId eq tenantId.toKotlinUuid()) and
              (IssueSubtypeConstraintsTable.childIssueTypeId eq issueTypeId.toKotlinUuid()) and
              (IssueSubtypeConstraintsTable.isActive eq true) and
              IssueSubtypeConstraintsTable.projectId.isNull()
          }
          .limit(1)
          .any()
    }
}

private fun requireValidCardinality(command: CreateIssueSubtypeConstraintCommand) {
  val min = command.minChildren
  val max = command.maxChildren
  requireValid(min == null || min >= 0, WorkbenchErrorCode.WORK_ITEM_SUBTYPE_CONSTRAINT_INVALID)
  requireValid(max == null || max >= 0, WorkbenchErrorCode.WORK_ITEM_SUBTYPE_CONSTRAINT_INVALID)
  requireValid(
    min == null || max == null || min <= max,
    WorkbenchErrorCode.WORK_ITEM_SUBTYPE_CONSTRAINT_INVALID,
  )
}

private fun findActiveIssueType(
  tenantId: UUID,
  apiIdOrCode: String,
  projectId: UUID?,
): ResultRow? {
  val scope =
    projectId?.let {
      IssueTypesTable.projectId.isNull() or (IssueTypesTable.projectId eq it.toKotlinUuid())
    } ?: IssueTypesTable.projectId.isNull()
  return IssueTypesTable.selectAll()
    .where {
      (IssueTypesTable.tenantId eq tenantId.toKotlinUuid()) and
        ((IssueTypesTable.apiId eq apiIdOrCode) or (IssueTypesTable.code eq apiIdOrCode)) and
        scope and
        (IssueTypesTable.isActive eq true) and
        IssueTypesTable.deletedAt.isNull()
    }
    .singleOrNull()
}

private fun findEffectiveConstraint(
  tenantId: UUID,
  projectId: UUID,
  parentIssueTypeId: UUID,
  childIssueTypeId: UUID,
): ResultRow? {
  val base =
    (IssueSubtypeConstraintsTable.tenantId eq tenantId.toKotlinUuid()) and
      (IssueSubtypeConstraintsTable.parentIssueTypeId eq parentIssueTypeId.toKotlinUuid()) and
      (IssueSubtypeConstraintsTable.childIssueTypeId eq childIssueTypeId.toKotlinUuid()) and
      (IssueSubtypeConstraintsTable.isActive eq true)
  return IssueSubtypeConstraintsTable.selectAll()
    .where { base and (IssueSubtypeConstraintsTable.projectId eq projectId.toKotlinUuid()) }
    .singleOrNull()
    ?: IssueSubtypeConstraintsTable.selectAll()
      .where { base and IssueSubtypeConstraintsTable.projectId.isNull() }
      .singleOrNull()
}

internal fun ResultRow.toIssueSubtypeConstraintRecord(): IssueSubtypeConstraintRecord =
  IssueSubtypeConstraintRecord(
    id = this[IssueSubtypeConstraintsTable.id].toJavaUuid(),
    tenantId = this[IssueSubtypeConstraintsTable.tenantId].toJavaUuid(),
    projectId = this[IssueSubtypeConstraintsTable.projectId]?.toJavaUuid(),
    parentIssueTypeId = this[IssueSubtypeConstraintsTable.parentIssueTypeId].toJavaUuid(),
    parentIssueTypeApiId =
      requirePublicId(
        IssueTypesTable,
        this[IssueSubtypeConstraintsTable.parentIssueTypeId].toJavaUuid(),
      ),
    childIssueTypeId = this[IssueSubtypeConstraintsTable.childIssueTypeId].toJavaUuid(),
    childIssueTypeApiId =
      requirePublicId(
        IssueTypesTable,
        this[IssueSubtypeConstraintsTable.childIssueTypeId].toJavaUuid(),
      ),
    isDefault = this[IssueSubtypeConstraintsTable.isDefault],
    minChildren = this[IssueSubtypeConstraintsTable.minChildren],
    maxChildren = this[IssueSubtypeConstraintsTable.maxChildren],
    isActive = this[IssueSubtypeConstraintsTable.isActive],
    createdBy = this[IssueSubtypeConstraintsTable.createdBy]?.toJavaUuid(),
    createdAt = this[IssueSubtypeConstraintsTable.createdAt],
    updatedAt = this[IssueSubtypeConstraintsTable.updatedAt],
  )
