package ink.doa.workbench.data.repository.workitem

import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.WorkItemCatalogRepository
import ink.doa.workbench.core.workitem.WorkflowConfigurationRepository
import ink.doa.workbench.core.workitem.model.CreateWorkflowCommand
import ink.doa.workbench.core.workitem.model.CreateWorkflowTransitionCommand
import ink.doa.workbench.core.workitem.model.WorkflowRecord
import ink.doa.workbench.core.workitem.model.WorkflowTransitionRecord
import ink.doa.workbench.data.persistence.postgres.workitem.ExposedWorkItemConfigQueries.nextWorkflowVersion
import ink.doa.workbench.data.persistence.postgres.workitem.ExposedWorkItemConfigQueries.requireTransition
import ink.doa.workbench.data.persistence.postgres.workitem.ExposedWorkItemConfigUsageChecks.rejectIfActiveConfigsUseWorkflow
import ink.doa.workbench.data.persistence.postgres.workitem.WorkflowTransitionsTable
import ink.doa.workbench.data.persistence.postgres.workitem.WorkflowsTable
import ink.doa.workbench.data.persistence.postgres.workitem.now
import ink.doa.workbench.data.persistence.postgres.workitem.toWorkflowRecord
import ink.doa.workbench.data.persistence.postgres.workitem.toWorkflowTransitionRecord
import java.time.OffsetDateTime
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
class ExposedWorkflowConfigurationRepository(
  private val database: Database,
  private val catalog: WorkItemCatalogRepository,
) : WorkflowConfigurationRepository {
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
          catalog.findStatus(command.tenantId, fromStatusApiId)
            ?: throw ResourceNotFoundException(
              WorkbenchErrorCode.RESOURCE_WORK_ITEM_STATUS_NOT_FOUND
            )
        }
      val toStatus =
        catalog.findStatus(command.tenantId, command.toStatusApiId)
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
}
