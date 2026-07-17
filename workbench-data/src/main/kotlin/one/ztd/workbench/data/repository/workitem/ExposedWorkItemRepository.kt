package one.ztd.workbench.data.repository.workitem

import java.time.OffsetDateTime
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import one.ztd.workbench.agile.workitem.CreateWorkItemPersistenceCommand
import one.ztd.workbench.agile.workitem.ReassignSprintBatchCommand
import one.ztd.workbench.agile.workitem.ReassignSprintBatchResult
import one.ztd.workbench.agile.workitem.WorkItemRepository
import one.ztd.workbench.agile.workitem.model.DeleteWorkItemCommand
import one.ztd.workbench.agile.workitem.model.TransitionPersistenceCommand
import one.ztd.workbench.agile.workitem.model.UpdateWorkItemCommand
import one.ztd.workbench.agile.workitem.model.WorkItemMutationResult
import one.ztd.workbench.agile.workitem.model.WorkItemPropertyValue
import one.ztd.workbench.agile.workitem.model.WorkItemRecord
import one.ztd.workbench.agile.workitem.stream.WorkItemEventCodec
import one.ztd.workbench.data.messaging.WorkItemOutboxAppender
import one.ztd.workbench.data.persistence.postgres.identity.UsersTable
import one.ztd.workbench.data.persistence.postgres.project.ProjectsTable
import one.ztd.workbench.data.persistence.postgres.workitem.InsertHierarchyLinkCommand
import one.ztd.workbench.data.persistence.postgres.workitem.InsertWorkItemRowsCommand
import one.ztd.workbench.data.persistence.postgres.workitem.IssueHierarchyTable
import one.ztd.workbench.data.persistence.postgres.workitem.IssuePropertyValuesTable
import one.ztd.workbench.data.persistence.postgres.workitem.IssueSprintChange
import one.ztd.workbench.data.persistence.postgres.workitem.IssuesTable
import one.ztd.workbench.data.persistence.postgres.workitem.PreparedWorkItemInsert
import one.ztd.workbench.data.persistence.postgres.workitem.SprintsTable
import one.ztd.workbench.data.persistence.postgres.workitem.StatusHistoryEntry
import one.ztd.workbench.data.persistence.postgres.workitem.WorkItemTransitionCompletion
import one.ztd.workbench.data.persistence.postgres.workitem.appendWorkItemEvent
import one.ztd.workbench.data.persistence.postgres.workitem.asObject
import one.ztd.workbench.data.persistence.postgres.workitem.completeWorkItemTransition
import one.ztd.workbench.data.persistence.postgres.workitem.insertHierarchyLink
import one.ztd.workbench.data.persistence.postgres.workitem.insertStatusHistory
import one.ztd.workbench.data.persistence.postgres.workitem.insertWorkItemRows
import one.ztd.workbench.data.persistence.postgres.workitem.now
import one.ztd.workbench.data.persistence.postgres.workitem.prepareWorkItemInsert
import one.ztd.workbench.data.persistence.postgres.workitem.propertyCode
import one.ztd.workbench.data.persistence.postgres.workitem.recordIssueSprintChange
import one.ztd.workbench.data.persistence.postgres.workitem.replacePropertyValues
import one.ztd.workbench.data.persistence.postgres.workitem.requireIssueRow
import one.ztd.workbench.data.persistence.postgres.workitem.requireStatus
import one.ztd.workbench.data.persistence.postgres.workitem.requireWorkItem
import one.ztd.workbench.data.persistence.postgres.workitem.resolvePriority
import one.ztd.workbench.data.persistence.postgres.workitem.resolveSprint
import one.ztd.workbench.data.persistence.postgres.workitem.resolveUser
import one.ztd.workbench.data.persistence.postgres.workitem.snapshot
import one.ztd.workbench.data.persistence.postgres.workitem.toJsonValue
import one.ztd.workbench.data.persistence.postgres.workitem.toWorkItemRecord
import one.ztd.workbench.kernel.common.ids.PublicId
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

private data class WorkItemUpdateState(
  val issueId: UUID,
  val before: WorkItemRecord,
  val previousSprintId: UUID?,
  val nextSprintId: UUID?,
  val snapshot: JsonObject,
  val changedAt: OffsetDateTime,
)

private data class ReassignmentEventInput(
  val command: ReassignSprintBatchCommand,
  val issueId: UUID,
  val before: WorkItemRecord,
  val after: WorkItemRecord,
  val targetSprintApiId: String?,
  val changedAt: OffsetDateTime,
)

@Repository
class ExposedWorkItemRepository(
  private val database: Database,
  private val eventFactory: WorkItemEventFactory,
  private val eventCodec: WorkItemEventCodec,
  private val outboxAppender: WorkItemOutboxAppender,
) : WorkItemRepository {
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
      recordCreatedRelations(command, prepared)
      createdResult(command, prepared).also { outboxAppender.append(it) }
    }

  private fun recordCreatedRelations(
    command: CreateWorkItemPersistenceCommand,
    prepared: PreparedWorkItemInsert,
  ) {
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
  }

  private fun createdResult(
    command: CreateWorkItemPersistenceCommand,
    prepared: PreparedWorkItemInsert,
  ): WorkItemMutationResult {
    val context =
      WorkItemActivityContext(
        tenantId = command.command.tenantId,
        projectId = command.command.projectId,
        workItemId = prepared.issueId,
        actorUserId = command.command.actorUserId,
        occurredAt = prepared.now,
      )
    val event =
      appendWorkItemEvent(
        eventCodec,
        eventFactory.created(context, command.issueTypeId, command.initialStatusId),
      )
    return WorkItemMutationResult(
      workItem =
        requireWorkItem(
          command.command.tenantId,
          command.command.projectId,
          prepared.issueApiId.value,
        ),
      eventType = "work_item.created",
      streamEventId = event.id,
      streamEventApiId = event.apiId,
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

  override suspend fun update(
    command: UpdateWorkItemCommand,
    propertyValues: List<WorkItemPropertyValue>,
  ): WorkItemMutationResult =
    suspendTransaction(db = database) {
      val state = prepareUpdate(command, propertyValues)
      persistUpdate(command, state)
      recordUpdatedSprint(command, state)
      replacePropertyValues(
        command.tenantId,
        state.issueId,
        propertyValues,
        command.actorUserId,
        state.changedAt,
      )
      val after = requireWorkItem(command.tenantId, command.projectId, command.workItemApiId)
      updatedResult(command, propertyValues, state, after).also { outboxAppender.append(it) }
    }

  private fun prepareUpdate(
    command: UpdateWorkItemCommand,
    propertyValues: List<WorkItemPropertyValue>,
  ): WorkItemUpdateState {
    val issue = requireIssueRow(command.tenantId, command.projectId, command.workItemApiId)
    val previousSprintId = issue[IssuesTable.sprintId]?.toJavaUuid()
    val sprintApiId = command.sprintApiId
    val nextSprintId =
      when {
        command.clearSprint -> null
        sprintApiId != null -> resolveSprint(command.tenantId, command.projectId, sprintApiId)
        else -> previousSprintId
      }
    return WorkItemUpdateState(
      issueId = issue[IssuesTable.id].toJavaUuid(),
      before = issue.toWorkItemRecord(),
      previousSprintId = previousSprintId,
      nextSprintId = nextSprintId,
      snapshot =
        JsonObject(issue[IssuesTable.propertiesSnapshot].asObject() + snapshot(propertyValues)),
      changedAt = now(),
    )
  }

  private fun persistUpdate(command: UpdateWorkItemCommand, state: WorkItemUpdateState) {
    IssuesTable.update({
      (IssuesTable.tenantId eq command.tenantId.toKotlinUuid()) and
        (IssuesTable.id eq state.issueId.toKotlinUuid())
    }) {
      command.title?.let { value -> it[IssuesTable.title] = value }
      command.description?.let { value -> it[IssuesTable.description] = value }
      command.descriptionPlainText?.let { value -> it[IssuesTable.descriptionPlainText] = value }
      command.assigneeApiId?.let { value ->
        it[IssuesTable.assigneeId] = resolveUser(value).toKotlinUuid()
      }
      command.priorityApiId?.let { value ->
        it[IssuesTable.priorityId] = resolvePriority(command.tenantId, value).toKotlinUuid()
      }
      if (command.clearSprint || command.sprintApiId != null) {
        it[IssuesTable.sprintId] = state.nextSprintId?.toKotlinUuid()
      }
      it[IssuesTable.propertiesSnapshot] = state.snapshot
      it[IssuesTable.updatedBy] = command.actorUserId.toKotlinUuid()
      it[IssuesTable.updatedAt] = state.changedAt
    }
  }

  private fun recordUpdatedSprint(command: UpdateWorkItemCommand, state: WorkItemUpdateState) {
    if (!command.clearSprint && command.sprintApiId == null) return
    recordIssueSprintChange(
      IssueSprintChange(
        tenantId = command.tenantId,
        issueId = state.issueId,
        previousSprintId = state.previousSprintId,
        nextSprintId = state.nextSprintId,
        actorUserId = command.actorUserId,
        changedAt = state.changedAt,
      )
    )
  }

  private fun updatedResult(
    command: UpdateWorkItemCommand,
    propertyValues: List<WorkItemPropertyValue>,
    state: WorkItemUpdateState,
    after: WorkItemRecord,
  ): WorkItemMutationResult {
    val context =
      WorkItemActivityContext(
        tenantId = command.tenantId,
        projectId = command.projectId,
        workItemId = state.issueId,
        actorUserId = command.actorUserId,
        occurredAt = state.changedAt,
      )
    val event =
      eventFactory
        .updated(WorkItemUpdateActivityInput(context, state.before, after, command, propertyValues))
        ?.let { appendWorkItemEvent(eventCodec, it) }
    return WorkItemMutationResult(
      workItem = after,
      eventType = "work_item.updated",
      streamEventId = event?.id,
      streamEventApiId = event?.apiId,
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
        .also { outboxAppender.append(it) }
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
        .also { outboxAppender.append(it) }
    }

  override suspend fun reassignSprintBatch(
    command: ReassignSprintBatchCommand
  ): ReassignSprintBatchResult =
    suspendTransaction(db = database) {
      val sourceSprintApiId = sprintApiId(command.sourceSprintId)
      val targetSprintApiId = command.targetSprintId?.let(::sprintApiId)
      val changed =
        reassignmentCandidates(command).mapNotNull { row ->
          reassignCandidate(command, row, sourceSprintApiId, targetSprintApiId)
        }
      ReassignSprintBatchResult(
        processedItems = changed.size,
        remainingItems = remainingReassignments(command),
        changedItems = changed,
      )
    }

  private fun reassignmentCandidates(command: ReassignSprintBatchCommand): List<ResultRow> =
    activeSprintIssues(command)
      .mapNotNull { row ->
        row.takeUnless { requireStatus(it[IssuesTable.statusId].toJavaUuid()).group == "done" }
      }
      .take(command.limit)

  private fun reassignCandidate(
    command: ReassignSprintBatchCommand,
    row: ResultRow,
    sourceSprintApiId: String,
    targetSprintApiId: String?,
  ): WorkItemRecord? {
    val issueId = row[IssuesTable.id].toJavaUuid()
    val before = row.toWorkItemRecord()
    val changedAt = now()
    if (!updateCandidateSprint(command, row, changedAt)) return null
    recordIssueSprintChange(
      IssueSprintChange(
        tenantId = command.tenantId,
        issueId = issueId,
        previousSprintId = command.sourceSprintId,
        nextSprintId = command.targetSprintId,
        actorUserId = command.actorUserId,
        changedAt = changedAt,
      )
    )
    val after = requireWorkItem(command.tenantId, command.projectId, before.apiId.value)
    appendReassignmentEvent(
      ReassignmentEventInput(command, issueId, before, after, targetSprintApiId, changedAt)
    )
    outboxAppender.appendSprintChanged(after, command, sourceSprintApiId, targetSprintApiId)
    return after
  }

  private fun updateCandidateSprint(
    command: ReassignSprintBatchCommand,
    row: ResultRow,
    changedAt: OffsetDateTime,
  ): Boolean =
    IssuesTable.update({
      (IssuesTable.id eq row[IssuesTable.id]) and
        (IssuesTable.sprintId eq command.sourceSprintId.toKotlinUuid())
    }) {
      it[IssuesTable.sprintId] = command.targetSprintId?.toKotlinUuid()
      it[IssuesTable.updatedBy] = command.actorUserId.toKotlinUuid()
      it[IssuesTable.updatedAt] = changedAt
    } > 0

  private fun appendReassignmentEvent(input: ReassignmentEventInput) {
    val command = input.command
    val updateCommand =
      UpdateWorkItemCommand(
        tenantId = command.tenantId,
        projectId = command.projectId,
        workItemApiId = input.before.apiId.value,
        sprintApiId = input.targetSprintApiId,
        clearSprint = command.targetSprintId == null,
        actorUserId = command.actorUserId,
      )
    val context =
      WorkItemActivityContext(
        tenantId = command.tenantId,
        projectId = command.projectId,
        workItemId = input.issueId,
        actorUserId = command.actorUserId,
        occurredAt = input.changedAt,
      )
    eventFactory
      .updated(
        WorkItemUpdateActivityInput(
          context,
          input.before,
          input.after,
          updateCommand,
          emptyList(),
        )
      )
      ?.let { appendWorkItemEvent(eventCodec, it) }
  }

  private fun remainingReassignments(command: ReassignSprintBatchCommand): Int =
    activeSprintIssues(command)
      .count { row -> requireStatus(row[IssuesTable.statusId].toJavaUuid()).group != "done" }
      .toInt()

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

private fun sprintApiId(id: UUID): String =
  SprintsTable.selectAll()
    .where { SprintsTable.id eq id.toKotlinUuid() }
    .single()[SprintsTable.apiId]

private fun activeSprintIssues(command: ReassignSprintBatchCommand) =
  IssuesTable.selectAll().where {
    (IssuesTable.tenantId eq command.tenantId.toKotlinUuid()) and
      (IssuesTable.projectId eq command.projectId.toKotlinUuid()) and
      (IssuesTable.sprintId eq command.sourceSprintId.toKotlinUuid()) and
      IssuesTable.archivedAt.isNull() and
      IssuesTable.deletedAt.isNull()
  }
