package ink.doa.workbench.data.repository.workitem

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.CreateWorkItemPersistenceCommand
import ink.doa.workbench.core.workitem.ReassignSprintBatchCommand
import ink.doa.workbench.core.workitem.ReassignSprintBatchResult
import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.model.DeleteWorkItemCommand
import ink.doa.workbench.core.workitem.model.TransitionPersistenceCommand
import ink.doa.workbench.core.workitem.model.UpdateWorkItemCommand
import ink.doa.workbench.core.workitem.model.WorkItemMutationResult
import ink.doa.workbench.core.workitem.model.WorkItemPropertyValue
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.stream.WorkItemEventCodec
import ink.doa.workbench.data.persistence.postgres.identity.UsersTable
import ink.doa.workbench.data.persistence.postgres.project.ProjectsTable
import ink.doa.workbench.data.persistence.postgres.workitem.InsertHierarchyLinkCommand
import ink.doa.workbench.data.persistence.postgres.workitem.InsertWorkItemRowsCommand
import ink.doa.workbench.data.persistence.postgres.workitem.IssueHierarchyTable
import ink.doa.workbench.data.persistence.postgres.workitem.IssuePropertyValuesTable
import ink.doa.workbench.data.persistence.postgres.workitem.IssueSprintChange
import ink.doa.workbench.data.persistence.postgres.workitem.IssuesTable
import ink.doa.workbench.data.persistence.postgres.workitem.SprintsTable
import ink.doa.workbench.data.persistence.postgres.workitem.StatusHistoryEntry
import ink.doa.workbench.data.persistence.postgres.workitem.WorkItemTransitionCompletion
import ink.doa.workbench.data.persistence.postgres.workitem.appendWorkItemEvent
import ink.doa.workbench.data.persistence.postgres.workitem.asObject
import ink.doa.workbench.data.persistence.postgres.workitem.completeWorkItemTransition
import ink.doa.workbench.data.persistence.postgres.workitem.insertHierarchyLink
import ink.doa.workbench.data.persistence.postgres.workitem.insertStatusHistory
import ink.doa.workbench.data.persistence.postgres.workitem.insertWorkItemRows
import ink.doa.workbench.data.persistence.postgres.workitem.now
import ink.doa.workbench.data.persistence.postgres.workitem.prepareWorkItemInsert
import ink.doa.workbench.data.persistence.postgres.workitem.propertyCode
import ink.doa.workbench.data.persistence.postgres.workitem.recordIssueSprintChange
import ink.doa.workbench.data.persistence.postgres.workitem.replacePropertyValues
import ink.doa.workbench.data.persistence.postgres.workitem.requireIssueRow
import ink.doa.workbench.data.persistence.postgres.workitem.requireStatus
import ink.doa.workbench.data.persistence.postgres.workitem.requireWorkItem
import ink.doa.workbench.data.persistence.postgres.workitem.resolvePriority
import ink.doa.workbench.data.persistence.postgres.workitem.resolveSprint
import ink.doa.workbench.data.persistence.postgres.workitem.resolveUser
import ink.doa.workbench.data.persistence.postgres.workitem.snapshot
import ink.doa.workbench.data.persistence.postgres.workitem.toJsonValue
import ink.doa.workbench.data.persistence.postgres.workitem.toWorkItemRecord
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
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

@Suppress("TooManyFunctions")
@Repository
class ExposedWorkItemRepository(
  private val database: Database,
  private val eventFactory: WorkItemEventFactory,
  private val eventCodec: WorkItemEventCodec,
) : WorkItemRepository {
  @Suppress("LongMethod")
  override suspend fun create(command: CreateWorkItemPersistenceCommand): WorkItemMutationResult =
    suspendTransaction(db = database) {
      val prepared = prepareWorkItemInsert(command.command, command.propertyValues)
      insertWorkItemRows(
        InsertWorkItemRowsCommand(
          command = command.command,
          prepared = prepared,
          issueTypeId = command.issueTypeId,
          issueTypeConfigId = command.issueTypeConfigId,
          initialStatusId = command.initialStatusId,
          propertyValues = command.propertyValues,
        )
      )
      command.parentIssueId?.let { parentIssueId ->
        insertHierarchyLink(
          InsertHierarchyLinkCommand(
            tenantId = command.command.tenantId,
            projectId = command.command.projectId,
            parentIssueId = parentIssueId,
            childIssueId = prepared.issueId,
            actorUserId = command.command.actorUserId,
            createdAt = prepared.now,
          )
        )
      }
      insertStatusHistory(
        StatusHistoryEntry(
          tenantId = command.command.tenantId,
          issueId = prepared.issueId,
          fromStatusId = null,
          toStatusId = command.initialStatusId,
          transitionId = null,
          actorUserId = command.command.actorUserId,
          changedAt = prepared.now,
        )
      )
      prepared.sprintId?.let { sprintId ->
        recordIssueSprintChange(
          IssueSprintChange(
            tenantId = command.command.tenantId,
            issueId = prepared.issueId,
            previousSprintId = null,
            nextSprintId = sprintId,
            actorUserId = command.command.actorUserId,
            changedAt = prepared.now,
          )
        )
      }
      val insertedEvent =
        appendWorkItemEvent(
          eventCodec,
          eventFactory.created(
            context =
              WorkItemActivityContext(
                tenantId = command.command.tenantId,
                projectId = command.command.projectId,
                workItemId = prepared.issueId,
                actorUserId = command.command.actorUserId,
                occurredAt = prepared.now,
              ),
            issueTypeId = command.issueTypeId,
            initialStatusId = command.initialStatusId,
          ),
        )
      WorkItemMutationResult(
        workItem =
          requireWorkItem(
            command.command.tenantId,
            command.command.projectId,
            prepared.issueApiId.value,
          ),
        eventType = "work_item.created",
        streamEventId = insertedEvent.id,
        streamEventApiId = insertedEvent.apiId,
      )
    }

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

  override suspend fun countUnfinishedBySprint(
    tenantId: UUID,
    projectId: UUID,
    sprintId: UUID,
  ): Long =
    suspendTransaction(db = database) {
      IssuesTable.selectAll()
        .where {
          (IssuesTable.tenantId eq tenantId.toKotlinUuid()) and
            (IssuesTable.projectId eq projectId.toKotlinUuid()) and
            (IssuesTable.sprintId eq sprintId.toKotlinUuid()) and
            IssuesTable.archivedAt.isNull() and
            IssuesTable.deletedAt.isNull()
        }
        .count { row -> requireStatus(row[IssuesTable.statusId].toJavaUuid()).group != "done" }
        .toLong()
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

  @Suppress("LongMethod")
  override suspend fun update(
    command: UpdateWorkItemCommand,
    propertyValues: List<WorkItemPropertyValue>,
  ): WorkItemMutationResult =
    suspendTransaction(db = database) {
      val issue = requireIssueRow(command.tenantId, command.projectId, command.workItemApiId)
      val before = issue.toWorkItemRecord()
      val issueId = issue[IssuesTable.id].toJavaUuid()
      val previousSprintId = issue[IssuesTable.sprintId]?.toJavaUuid()
      val current = issue[IssuesTable.propertiesSnapshot].asObject()
      val snapshot = JsonObject(current + snapshot(propertyValues))
      val now = now()
      val sprintApiId = command.sprintApiId
      val nextSprintId =
        when {
          command.clearSprint -> null
          sprintApiId != null -> resolveSprint(command.tenantId, command.projectId, sprintApiId)
          else -> previousSprintId
        }
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
        if (command.clearSprint || sprintApiId != null) {
          it[IssuesTable.sprintId] = nextSprintId?.toKotlinUuid()
        }
        it[IssuesTable.propertiesSnapshot] = snapshot
        it[IssuesTable.updatedBy] = command.actorUserId.toKotlinUuid()
        it[IssuesTable.updatedAt] = now
      }
      if (command.clearSprint || sprintApiId != null) {
        recordIssueSprintChange(
          IssueSprintChange(
            tenantId = command.tenantId,
            issueId = issueId,
            previousSprintId = previousSprintId,
            nextSprintId = nextSprintId,
            actorUserId = command.actorUserId,
            changedAt = now,
          )
        )
      }
      replacePropertyValues(command.tenantId, issueId, propertyValues, command.actorUserId, now)
      val after = requireWorkItem(command.tenantId, command.projectId, command.workItemApiId)
      val updateEvent =
        eventFactory
          .updated(
            WorkItemUpdateActivityInput(
              context =
                WorkItemActivityContext(
                  tenantId = command.tenantId,
                  projectId = command.projectId,
                  workItemId = issueId,
                  actorUserId = command.actorUserId,
                  occurredAt = now,
                ),
              before = before,
              after = after,
              command = command,
              propertyValues = propertyValues,
            )
          )
          ?.let { appendWorkItemEvent(eventCodec, it) }
      WorkItemMutationResult(
        workItem = after,
        eventType = "work_item.updated",
        streamEventId = updateEvent?.id,
        streamEventApiId = updateEvent?.apiId,
      )
    }

  override suspend fun transition(
    command: TransitionPersistenceCommand,
    fromStatusId: UUID,
    toStatusId: UUID,
    transitionId: UUID,
    propertyValues: List<WorkItemPropertyValue>,
  ): WorkItemMutationResult =
    suspendTransaction(db = database) {
      val issue = requireIssueRow(command.tenantId, command.projectId, command.workItemApiId)
      val issueId = issue[IssuesTable.id].toJavaUuid()
      val previousSprintId = issue[IssuesTable.sprintId]?.toJavaUuid()
      val current = issue[IssuesTable.propertiesSnapshot].asObject()
      val snapshot = JsonObject(current + snapshot(propertyValues))
      val now = now()
      val nextSprintId =
        command.sprintApiId?.let { value ->
          resolveSprint(command.tenantId, command.projectId, value)
        } ?: previousSprintId
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
        if (command.sprintApiId != null) {
          it[IssuesTable.sprintId] = nextSprintId?.toKotlinUuid()
        }
        it[IssuesTable.propertiesSnapshot] = snapshot
        it[IssuesTable.updatedBy] = command.actorUserId.toKotlinUuid()
        it[IssuesTable.updatedAt] = now
      }
      completeWorkItemTransition(
        WorkItemTransitionCompletion(
          command = command,
          issueId = issueId,
          fromStatusId = fromStatusId,
          toStatusId = toStatusId,
          transitionId = transitionId,
          propertyValues = propertyValues,
          previousSprintId = previousSprintId,
          nextSprintId = nextSprintId,
          now = now,
        ),
        eventFactory = eventFactory,
        eventCodec = eventCodec,
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

  @Suppress("LongMethod")
  override suspend fun reassignSprintBatch(
    command: ReassignSprintBatchCommand
  ): ReassignSprintBatchResult =
    suspendTransaction(db = database) {
      val targetSprintApiId =
        command.targetSprintId?.let { targetId ->
          SprintsTable.selectAll()
            .where { SprintsTable.id eq targetId.toKotlinUuid() }
            .single()[SprintsTable.apiId]
        }
      val candidates =
        IssuesTable.selectAll()
          .where {
            (IssuesTable.tenantId eq command.tenantId.toKotlinUuid()) and
              (IssuesTable.projectId eq command.projectId.toKotlinUuid()) and
              (IssuesTable.sprintId eq command.sourceSprintId.toKotlinUuid()) and
              IssuesTable.archivedAt.isNull() and
              IssuesTable.deletedAt.isNull()
          }
          .mapNotNull { row ->
            row.takeUnless { requireStatus(it[IssuesTable.statusId].toJavaUuid()).group == "done" }
          }
          .take(command.limit)
      val changed = candidates.mapNotNull { row ->
        val issueId = row[IssuesTable.id].toJavaUuid()
        val before = row.toWorkItemRecord()
        val now = now()
        val updated =
          IssuesTable.update({
            (IssuesTable.id eq row[IssuesTable.id]) and
              (IssuesTable.sprintId eq command.sourceSprintId.toKotlinUuid())
          }) {
            it[IssuesTable.sprintId] = command.targetSprintId?.toKotlinUuid()
            it[IssuesTable.updatedBy] = command.actorUserId.toKotlinUuid()
            it[IssuesTable.updatedAt] = now
          }
        if (updated == 0) return@mapNotNull null
        recordIssueSprintChange(
          IssueSprintChange(
            tenantId = command.tenantId,
            issueId = issueId,
            previousSprintId = command.sourceSprintId,
            nextSprintId = command.targetSprintId,
            actorUserId = command.actorUserId,
            changedAt = now,
          )
        )
        val after = requireWorkItem(command.tenantId, command.projectId, before.apiId.value)
        eventFactory
          .updated(
            WorkItemUpdateActivityInput(
              context =
                WorkItemActivityContext(
                  tenantId = command.tenantId,
                  projectId = command.projectId,
                  workItemId = issueId,
                  actorUserId = command.actorUserId,
                  occurredAt = now,
                ),
              before = before,
              after = after,
              command =
                UpdateWorkItemCommand(
                  tenantId = command.tenantId,
                  projectId = command.projectId,
                  workItemApiId = before.apiId.value,
                  sprintApiId = targetSprintApiId,
                  clearSprint = command.targetSprintId == null,
                  actorUserId = command.actorUserId,
                ),
              propertyValues = emptyList(),
            )
          )
          ?.let { appendWorkItemEvent(eventCodec, it) }
        after
      }
      val remaining =
        IssuesTable.selectAll()
          .where {
            (IssuesTable.tenantId eq command.tenantId.toKotlinUuid()) and
              (IssuesTable.projectId eq command.projectId.toKotlinUuid()) and
              (IssuesTable.sprintId eq command.sourceSprintId.toKotlinUuid()) and
              IssuesTable.archivedAt.isNull() and
              IssuesTable.deletedAt.isNull()
          }
          .count { row -> requireStatus(row[IssuesTable.statusId].toJavaUuid()).group != "done" }
          .toInt()
      ReassignSprintBatchResult(
        processedItems = changed.size,
        remainingItems = remaining,
        changedItems = changed,
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
}
