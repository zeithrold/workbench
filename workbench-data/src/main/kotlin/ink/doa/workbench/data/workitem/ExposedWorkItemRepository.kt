package ink.doa.workbench.data.workitem

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.core.workitem.model.DeleteWorkItemCommand
import ink.doa.workbench.core.workitem.model.TransitionWorkItemCommand
import ink.doa.workbench.core.workitem.model.UpdateWorkItemCommand
import ink.doa.workbench.core.workitem.model.WorkItemMutationResult
import ink.doa.workbench.core.workitem.model.WorkItemPropertyValue
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.data.persistence.IssueHierarchyTable
import ink.doa.workbench.data.persistence.IssueKeyAliasesTable
import ink.doa.workbench.data.persistence.IssuePropertyValuesTable
import ink.doa.workbench.data.persistence.IssuesTable
import ink.doa.workbench.data.persistence.ProjectsTable
import ink.doa.workbench.data.persistence.UsersTable
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

@Repository
class ExposedWorkItemRepository(private val database: Database) : WorkItemRepository {
  override suspend fun create(
    command: CreateWorkItemCommand,
    issueTypeId: UUID,
    issueTypeConfigId: UUID,
    initialStatusId: UUID,
    propertyValues: List<WorkItemPropertyValue>,
    parentIssueId: UUID?,
  ): WorkItemMutationResult =
    suspendTransaction(db = database) {
      val project = requireProject(command.tenantId, command.projectId)
      val sequence = allocateSequence(project.id)
      val issueId = UUID.randomUUID()
      val issueApiId = PublicId.new("iss")
      val key = "${project.identifier}-$sequence"
      val now = now()
      val priorityId = command.priorityApiId?.let { resolvePriority(command.tenantId, it) }
      val assigneeId = command.assigneeApiId?.let { resolveUser(it) }
      val sprintId =
        command.sprintApiId?.let { resolveSprint(command.tenantId, command.projectId, it) }
      val snapshot = snapshot(propertyValues)
      IssuesTable.insert {
        it[IssuesTable.id] = issueId.toKotlinUuid()
        it[IssuesTable.apiId] = issueApiId.value
        it[IssuesTable.tenantId] = command.tenantId.toKotlinUuid()
        it[IssuesTable.projectId] = command.projectId.toKotlinUuid()
        it[IssuesTable.issueTypeId] = issueTypeId.toKotlinUuid()
        it[IssuesTable.issueTypeConfigId] = issueTypeConfigId.toKotlinUuid()
        it[IssuesTable.sequenceNo] = sequence
        it[IssuesTable.title] = command.title
        it[IssuesTable.description] = command.description
        it[IssuesTable.descriptionPlainText] = command.descriptionPlainText
        it[IssuesTable.statusId] = initialStatusId.toKotlinUuid()
        it[IssuesTable.priorityId] = priorityId?.toKotlinUuid()
        it[IssuesTable.reporterId] = command.reporterId.toKotlinUuid()
        it[IssuesTable.assigneeId] = assigneeId?.toKotlinUuid()
        it[IssuesTable.sprintId] = sprintId?.toKotlinUuid()
        it[IssuesTable.propertiesSnapshot] = snapshot
        it[IssuesTable.createdBy] = command.actorUserId.toKotlinUuid()
        it[IssuesTable.createdAt] = now
        it[IssuesTable.updatedAt] = now
      }
      IssueKeyAliasesTable.insert {
        it[IssueKeyAliasesTable.id] = UUID.randomUUID().toKotlinUuid()
        it[IssueKeyAliasesTable.tenantId] = command.tenantId.toKotlinUuid()
        it[IssueKeyAliasesTable.projectId] = command.projectId.toKotlinUuid()
        it[IssueKeyAliasesTable.issueId] = issueId.toKotlinUuid()
        it[IssueKeyAliasesTable.issueKey] = key
        it[IssueKeyAliasesTable.projectIdentifier] = project.identifier
        it[IssueKeyAliasesTable.sequenceNo] = sequence
        it[IssueKeyAliasesTable.isCurrent] = true
        it[IssueKeyAliasesTable.createdAt] = now
        it[IssueKeyAliasesTable.createdBy] = command.actorUserId.toKotlinUuid()
      }
      insertPropertyValues(command.tenantId, issueId, propertyValues, command.actorUserId, now)
      parentIssueId?.let {
        insertHierarchyLink(
          tenantId = command.tenantId,
          projectId = command.projectId,
          parentIssueId = it,
          childIssueId = issueId,
          actorUserId = command.actorUserId,
          createdAt = now,
        )
      }
      insertStatusHistory(
        tenantId = command.tenantId,
        issueId = issueId,
        fromStatusId = null,
        toStatusId = initialStatusId,
        transitionId = null,
        actorUserId = command.actorUserId,
        changedAt = now,
      )
      WorkItemMutationResult(
        workItem = requireWorkItem(command.tenantId, command.projectId, issueApiId.value),
        eventType = "work_item.created",
      )
    }

  suspend fun create(
    command: CreateWorkItemCommand,
    issueTypeId: UUID,
    issueTypeConfigId: UUID,
    initialStatusId: UUID,
    propertyValues: List<WorkItemPropertyValue>,
  ): WorkItemMutationResult =
    create(
      command = command,
      issueTypeId = issueTypeId,
      issueTypeConfigId = issueTypeConfigId,
      initialStatusId = initialStatusId,
      propertyValues = propertyValues,
      parentIssueId = null,
    )

  override suspend fun findByApiId(tenantId: UUID, apiId: String): WorkItemRecord? =
    suspendTransaction(db = database) {
      IssuesTable.selectAll()
        .where {
          (IssuesTable.tenantId eq tenantId.toKotlinUuid()) and
            (IssuesTable.apiId eq apiId) and
            IssuesTable.archivedAt.isNull() and
            IssuesTable.deletedAt.isNull()
        }
        .singleOrNull()
        ?.toWorkItemRecord()
    }

  override suspend fun findByApiId(
    tenantId: UUID,
    projectId: UUID,
    apiId: String,
  ): WorkItemRecord? =
    suspendTransaction(db = database) {
      IssuesTable.selectAll()
        .where {
          (IssuesTable.tenantId eq tenantId.toKotlinUuid()) and
            (IssuesTable.projectId eq projectId.toKotlinUuid()) and
            (IssuesTable.apiId eq apiId) and
            IssuesTable.archivedAt.isNull() and
            IssuesTable.deletedAt.isNull()
        }
        .singleOrNull()
        ?.toWorkItemRecord()
    }

  override suspend fun listByProject(
    tenantId: UUID,
    projectId: UUID,
    limit: Int,
    offset: Long,
  ): List<WorkItemRecord> =
    suspendTransaction(db = database) {
      IssuesTable.selectAll()
        .where {
          (IssuesTable.tenantId eq tenantId.toKotlinUuid()) and
            (IssuesTable.projectId eq projectId.toKotlinUuid()) and
            IssuesTable.archivedAt.isNull() and
            IssuesTable.deletedAt.isNull()
        }
        .orderBy(IssuesTable.updatedAt to SortOrder.DESC)
        .limit(limit)
        .offset(offset)
        .map { it.toWorkItemRecord() }
    }

  override suspend fun listPropertyValues(
    tenantId: UUID,
    issueId: UUID,
  ): Map<String, JsonElement> =
    suspendTransaction(db = database) {
      IssuePropertyValuesTable.selectAll()
        .where {
          (IssuePropertyValuesTable.tenantId eq tenantId.toKotlinUuid()) and
            (IssuePropertyValuesTable.issueId eq issueId.toKotlinUuid())
        }
        .associate { row ->
          val propertyId = row[IssuePropertyValuesTable.propertyId].toJavaUuid()
          propertyCode(propertyId) to row.toJsonValue()
        }
    }

  override suspend fun update(
    command: UpdateWorkItemCommand,
    propertyValues: List<WorkItemPropertyValue>,
  ): WorkItemMutationResult =
    suspendTransaction(db = database) {
      val issue = requireIssueRow(command.tenantId, command.projectId, command.workItemApiId)
      val issueId = issue[IssuesTable.id].toJavaUuid()
      val current = issue[IssuesTable.propertiesSnapshot].asObject()
      val snapshot = JsonObject(current + snapshot(propertyValues))
      val now = now()
      IssuesTable.update({
        (IssuesTable.tenantId eq command.tenantId.toKotlinUuid()) and
          (IssuesTable.id eq issue[IssuesTable.id])
      }) {
        command.title?.let { value -> it[IssuesTable.title] = value }
        command.description?.let { value -> it[IssuesTable.description] = value }
        command.descriptionPlainText?.let { value ->
          it[IssuesTable.descriptionPlainText] = value
        }
        command.assigneeApiId?.let { value ->
          it[IssuesTable.assigneeId] = resolveUser(value).toKotlinUuid()
        }
        command.priorityApiId?.let { value ->
          it[IssuesTable.priorityId] = resolvePriority(command.tenantId, value).toKotlinUuid()
        }
        command.sprintApiId?.let { value ->
          it[IssuesTable.sprintId] =
            resolveSprint(command.tenantId, command.projectId, value).toKotlinUuid()
        }
        it[IssuesTable.propertiesSnapshot] = snapshot
        it[IssuesTable.updatedBy] = command.actorUserId.toKotlinUuid()
        it[IssuesTable.updatedAt] = now
      }
      replacePropertyValues(command.tenantId, issueId, propertyValues, command.actorUserId, now)
      WorkItemMutationResult(
        workItem = requireWorkItem(command.tenantId, command.projectId, command.workItemApiId),
        eventType = "work_item.updated",
      )
    }

  override suspend fun transition(
    command: TransitionWorkItemCommand,
    fromStatusId: UUID,
    toStatusId: UUID,
    transitionId: UUID,
    propertyValues: List<WorkItemPropertyValue>,
  ): WorkItemMutationResult =
    suspendTransaction(db = database) {
      val issue = requireIssueRow(command.tenantId, command.projectId, command.workItemApiId)
      val issueId = issue[IssuesTable.id].toJavaUuid()
      val current = issue[IssuesTable.propertiesSnapshot].asObject()
      val snapshot = JsonObject(current + snapshot(propertyValues))
      val now = now()
      IssuesTable.update({
        (IssuesTable.tenantId eq command.tenantId.toKotlinUuid()) and
          (IssuesTable.id eq issue[IssuesTable.id]) and
          (IssuesTable.statusId eq fromStatusId.toKotlinUuid())
      }) {
        it[IssuesTable.statusId] = toStatusId.toKotlinUuid()
        command.title?.let { value -> it[IssuesTable.title] = value }
        command.description?.let { value -> it[IssuesTable.description] = value }
        command.descriptionPlainText?.let { value ->
          it[IssuesTable.descriptionPlainText] = value
        }
        command.assigneeApiId?.let { value ->
          it[IssuesTable.assigneeId] = resolveUser(value).toKotlinUuid()
        }
        command.priorityApiId?.let { value ->
          it[IssuesTable.priorityId] = resolvePriority(command.tenantId, value).toKotlinUuid()
        }
        command.sprintApiId?.let { value ->
          it[IssuesTable.sprintId] =
            resolveSprint(command.tenantId, command.projectId, value).toKotlinUuid()
        }
        it[IssuesTable.propertiesSnapshot] = snapshot
        it[IssuesTable.updatedBy] = command.actorUserId.toKotlinUuid()
        it[IssuesTable.updatedAt] = now
      }
      replacePropertyValues(command.tenantId, issueId, propertyValues, command.actorUserId, now)
      val statusHistoryId =
        insertStatusHistory(
          tenantId = command.tenantId,
          issueId = issueId,
          fromStatusId = fromStatusId,
          toStatusId = toStatusId,
          transitionId = transitionId,
          actorUserId = command.actorUserId,
          changedAt = now,
        )
      WorkItemMutationResult(
        workItem = requireWorkItem(command.tenantId, command.projectId, command.workItemApiId),
        eventType = "work_item.transitioned",
        statusHistoryId = statusHistoryId,
      )
    }

  override suspend fun softDelete(command: DeleteWorkItemCommand): WorkItemMutationResult =
    suspendTransaction(db = database) {
      val issue = requireIssueRow(command.tenantId, command.projectId, command.workItemApiId)
      val now = now()
      IssuesTable.update({ IssuesTable.id eq issue[IssuesTable.id] }) {
        it[IssuesTable.deletedAt] = now
        it[IssuesTable.deletedBy] = command.actorUserId.toKotlinUuid()
        it[IssuesTable.deleteReason] = command.deleteReason
        it[IssuesTable.updatedBy] = command.actorUserId.toKotlinUuid()
        it[IssuesTable.updatedAt] = now
      }
      val deleted =
        IssuesTable.selectAll()
          .where { IssuesTable.id eq issue[IssuesTable.id] }
          .single()
          .toWorkItemRecord()
      WorkItemMutationResult(
        workItem = deleted,
        eventType = "work_item.updated",
      )
    }

  override suspend fun countChildrenNotInStatusGroups(
    tenantId: UUID,
    issueId: UUID,
    terminalGroups: Set<String>,
  ): Long =
    suspendTransaction(db = database) {
      val childIds =
        IssueHierarchyTable.selectAll()
          .where {
            (IssueHierarchyTable.tenantId eq tenantId.toKotlinUuid()) and
              (IssueHierarchyTable.parentIssueId eq issueId.toKotlinUuid())
          }
          .map { it[IssueHierarchyTable.childIssueId] }
      if (childIds.isEmpty()) {
        return@suspendTransaction 0
      }
      IssuesTable.selectAll()
        .where {
          (IssuesTable.tenantId eq tenantId.toKotlinUuid()) and (IssuesTable.id inList childIds)
        }
        .count { row ->
          requireStatus(row[IssuesTable.statusId].toJavaUuid()).group !in terminalGroups
        }
        .toLong()
    }

  override suspend fun resolveUserApiId(userId: UUID): PublicId? =
    suspendTransaction(db = database) {
      UsersTable.selectAll()
        .where { UsersTable.id eq userId.toKotlinUuid() }
        .singleOrNull()
        ?.get(UsersTable.apiId)
        ?.let(::PublicId)
    }

  override suspend fun resolveProjectApiId(tenantId: UUID, projectId: UUID): PublicId? =
    suspendTransaction(db = database) {
      ProjectsTable.selectAll()
        .where {
          (ProjectsTable.tenantId eq tenantId.toKotlinUuid()) and
            (ProjectsTable.id eq projectId.toKotlinUuid())
        }
        .singleOrNull()
        ?.get(ProjectsTable.apiId)
        ?.let(::PublicId)
    }

  private fun insertHierarchyLink(
    tenantId: UUID,
    projectId: UUID,
    parentIssueId: UUID,
    childIssueId: UUID,
    actorUserId: UUID,
    createdAt: java.time.OffsetDateTime,
  ) {
    val nextRank =
      IssueHierarchyTable.selectAll()
        .where {
          (IssueHierarchyTable.tenantId eq tenantId.toKotlinUuid()) and
            (IssueHierarchyTable.parentIssueId eq parentIssueId.toKotlinUuid())
        }
        .orderBy(IssueHierarchyTable.rank to SortOrder.DESC)
        .limit(1)
        .singleOrNull()
        ?.get(IssueHierarchyTable.rank)
        ?.plus(100) ?: 100
    IssueHierarchyTable.insert {
      it[IssueHierarchyTable.id] = UUID.randomUUID().toKotlinUuid()
      it[IssueHierarchyTable.tenantId] = tenantId.toKotlinUuid()
      it[IssueHierarchyTable.projectId] = projectId.toKotlinUuid()
      it[IssueHierarchyTable.parentIssueId] = parentIssueId.toKotlinUuid()
      it[IssueHierarchyTable.childIssueId] = childIssueId.toKotlinUuid()
      it[IssueHierarchyTable.rank] = nextRank
      it[IssueHierarchyTable.createdBy] = actorUserId.toKotlinUuid()
      it[IssueHierarchyTable.createdAt] = createdAt
    }
  }
}
