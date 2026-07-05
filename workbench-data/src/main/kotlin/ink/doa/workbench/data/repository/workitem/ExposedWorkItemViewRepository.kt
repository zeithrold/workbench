@file:Suppress("TooManyFunctions")

package ink.doa.workbench.data.workitem

import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.view.CreateWorkItemViewCommand
import ink.doa.workbench.core.workitem.view.DeleteWorkItemViewCommand
import ink.doa.workbench.core.workitem.view.UpdateWorkItemViewCommand
import ink.doa.workbench.core.workitem.view.WorkItemViewRecord
import ink.doa.workbench.core.workitem.view.WorkItemViewRepository
import ink.doa.workbench.core.workitem.view.WorkItemViewVisibility
import ink.doa.workbench.data.persistence.UsersTable
import ink.doa.workbench.data.persistence.WorkItemViewsTable
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

@Repository
class ExposedWorkItemViewRepository(private val database: Database) : WorkItemViewRepository {
  override suspend fun listByProject(tenantId: UUID, projectId: UUID): List<WorkItemViewRecord> =
    suspendTransaction(db = database) {
      WorkItemViewsTable.selectAll()
        .where {
          (WorkItemViewsTable.tenantId eq tenantId.toKotlinUuid()) and
            (WorkItemViewsTable.projectId eq projectId.toKotlinUuid())
        }
        .orderBy(WorkItemViewsTable.updatedAt to SortOrder.DESC)
        .map { it.toRecord() }
    }

  override suspend fun listTenantScoped(tenantId: UUID): List<WorkItemViewRecord> =
    suspendTransaction(db = database) {
      WorkItemViewsTable.selectAll()
        .where {
          (WorkItemViewsTable.tenantId eq tenantId.toKotlinUuid()) and
            WorkItemViewsTable.projectId.isNull()
        }
        .orderBy(WorkItemViewsTable.updatedAt to SortOrder.DESC)
        .map { it.toRecord() }
    }

  override suspend fun findByApiId(
    tenantId: UUID,
    viewApiId: String,
    projectId: UUID?,
  ): WorkItemViewRecord? =
    suspendTransaction(db = database) {
      WorkItemViewsTable.selectAll()
        .where {
          var condition =
            (WorkItemViewsTable.tenantId eq tenantId.toKotlinUuid()) and
              (WorkItemViewsTable.apiId eq viewApiId)
          condition =
            if (projectId == null) {
              condition and WorkItemViewsTable.projectId.isNull()
            } else {
              condition and (WorkItemViewsTable.projectId eq projectId.toKotlinUuid())
            }
          condition
        }
        .singleOrNull()
        ?.toRecord()
    }

  override suspend fun create(command: CreateWorkItemViewCommand): WorkItemViewRecord =
    suspendTransaction(db = database) {
      val now = now()
      val viewId = UUID.randomUUID()
      val apiId = PublicId.new("wiv")
      WorkItemViewsTable.insert {
        it[WorkItemViewsTable.id] = viewId.toKotlinUuid()
        it[WorkItemViewsTable.apiId] = apiId.value
        it[WorkItemViewsTable.tenantId] = command.tenantId.toKotlinUuid()
        it[WorkItemViewsTable.projectId] = command.projectId?.toKotlinUuid()
        it[WorkItemViewsTable.ownerId] = command.ownerId.toKotlinUuid()
        it[WorkItemViewsTable.name] = command.name
        it[WorkItemViewsTable.description] = command.description
        it[WorkItemViewsTable.visibility] = command.visibility.dbValue
        it[WorkItemViewsTable.filterAst] = command.filterAst
        it[WorkItemViewsTable.sortAst] = command.sortAst
        it[WorkItemViewsTable.groupAst] = command.groupAst
        it[WorkItemViewsTable.displayFields] = command.displayFields
        it[WorkItemViewsTable.createdAt] = now
        it[WorkItemViewsTable.updatedAt] = now
      }
      WorkItemViewRecord(
        id = viewId,
        apiId = apiId,
        tenantId = command.tenantId,
        projectId = command.projectId,
        ownerId = command.ownerId,
        ownerApiId = requireUserApiId(command.ownerId),
        name = command.name,
        description = command.description,
        visibility = command.visibility,
        filterAst = command.filterAst,
        sortAst = command.sortAst,
        groupAst = command.groupAst,
        displayFields = command.displayFields,
        createdAt = now,
        updatedAt = now,
      )
    }

  override suspend fun update(command: UpdateWorkItemViewCommand): WorkItemViewRecord =
    suspendTransaction(db = database) {
      val row = requireRow(command.tenantId, command.viewApiId, command.projectId)
      val now = now()
      WorkItemViewsTable.update({ WorkItemViewsTable.id eq row[WorkItemViewsTable.id] }) {
        applyUpdate(it, command, now)
      }
      mergeUpdatedRecord(row.toRecord(), command, now)
    }

  private fun applyUpdate(
    builder: UpdateBuilder<*>,
    command: UpdateWorkItemViewCommand,
    now: OffsetDateTime,
  ) {
    command.name?.let { value -> builder[WorkItemViewsTable.name] = value }
    command.description?.let { value -> builder[WorkItemViewsTable.description] = value }
    command.visibility?.let { value -> builder[WorkItemViewsTable.visibility] = value.dbValue }
    command.filterAst?.let { value -> builder[WorkItemViewsTable.filterAst] = value }
    command.sortAst?.let { value -> builder[WorkItemViewsTable.sortAst] = value }
    command.groupAst?.let { value -> builder[WorkItemViewsTable.groupAst] = value }
    command.displayFields?.let { value -> builder[WorkItemViewsTable.displayFields] = value }
    builder[WorkItemViewsTable.updatedAt] = now
  }

  private fun mergeUpdatedRecord(
    record: WorkItemViewRecord,
    command: UpdateWorkItemViewCommand,
    now: OffsetDateTime,
  ): WorkItemViewRecord =
    record.copy(
      name = command.name ?: record.name,
      description = command.description ?: record.description,
      visibility = command.visibility ?: record.visibility,
      filterAst = command.filterAst ?: record.filterAst,
      sortAst = command.sortAst ?: record.sortAst,
      groupAst = command.groupAst ?: record.groupAst,
      displayFields = command.displayFields ?: record.displayFields,
      updatedAt = now,
    )

  override suspend fun delete(command: DeleteWorkItemViewCommand): Boolean =
    suspendTransaction(db = database) {
      val row = requireRow(command.tenantId, command.viewApiId, command.projectId)
      WorkItemViewsTable.deleteWhere { WorkItemViewsTable.id eq row[WorkItemViewsTable.id] } > 0
    }

  private fun requireRow(tenantId: UUID, viewApiId: String, projectId: UUID?): ResultRow =
    WorkItemViewsTable.selectAll()
      .where {
        var condition =
          (WorkItemViewsTable.tenantId eq tenantId.toKotlinUuid()) and
            (WorkItemViewsTable.apiId eq viewApiId)
        condition =
          if (projectId == null) {
            condition and WorkItemViewsTable.projectId.isNull()
          } else {
            condition and (WorkItemViewsTable.projectId eq projectId.toKotlinUuid())
          }
        condition
      }
      .singleOrNull()
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_VIEW_NOT_FOUND)

  private fun ResultRow.toRecord(): WorkItemViewRecord {
    val ownerId = this[WorkItemViewsTable.ownerId].toJavaUuid()
    return WorkItemViewRecord(
      id = this[WorkItemViewsTable.id].toJavaUuid(),
      apiId = PublicId(this[WorkItemViewsTable.apiId]),
      tenantId = this[WorkItemViewsTable.tenantId].toJavaUuid(),
      projectId = this[WorkItemViewsTable.projectId]?.toJavaUuid(),
      ownerId = ownerId,
      ownerApiId = requireUserApiId(ownerId),
      name = this[WorkItemViewsTable.name],
      description = this[WorkItemViewsTable.description],
      visibility = WorkItemViewVisibility.fromDbValue(this[WorkItemViewsTable.visibility]),
      filterAst = this[WorkItemViewsTable.filterAst],
      sortAst = this[WorkItemViewsTable.sortAst],
      groupAst = this[WorkItemViewsTable.groupAst],
      displayFields = this[WorkItemViewsTable.displayFields],
      createdAt = this[WorkItemViewsTable.createdAt],
      updatedAt = this[WorkItemViewsTable.updatedAt],
    )
  }

  private fun requireUserApiId(userId: UUID): PublicId =
    UsersTable.selectAll()
      .where { UsersTable.id eq userId.toKotlinUuid() }
      .singleOrNull()
      ?.let { PublicId(it[UsersTable.apiId]) }
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND)

  private fun now(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)
}
