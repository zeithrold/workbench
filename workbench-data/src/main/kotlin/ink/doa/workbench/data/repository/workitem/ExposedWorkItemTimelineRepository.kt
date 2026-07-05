package ink.doa.workbench.data.repository.workitem

import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.pagination.WorkbenchCursor
import ink.doa.workbench.core.common.pagination.WorkbenchTimelineEntryKind
import ink.doa.workbench.core.workitem.WorkItemTimelineRepository
import ink.doa.workbench.core.workitem.timeline.ListWorkItemTimelineQuery
import ink.doa.workbench.core.workitem.timeline.WorkItemTimelineEntry
import ink.doa.workbench.core.workitem.timeline.WorkItemTimelinePage
import ink.doa.workbench.data.persistence.postgres.workitem.IssuesTable
import ink.doa.workbench.data.persistence.postgres.workitem.WorkItemTimelineEntriesTable
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.springframework.stereotype.Repository

@Repository
class ExposedWorkItemTimelineRepository(
  private val database: Database,
  private val activities: ExposedWorkItemActivityRepository,
  private val comments: ExposedWorkItemCommentRepository,
) : WorkItemTimelineRepository {
  override suspend fun listByWorkItem(query: ListWorkItemTimelineQuery): WorkItemTimelinePage =
    suspendTransaction(db = database) {
      val workItemId = resolveWorkItemId(query.tenantId, query.projectId, query.workItemApiId)
      val limit = query.limit.coerceIn(MIN_LIMIT, MAX_LIMIT)
      val fetchLimit = limit + 1
      val rows =
        WorkItemTimelineEntriesTable.selectAll()
          .where {
            var condition =
              (WorkItemTimelineEntriesTable.tenantId eq query.tenantId.toKotlinUuid()) and
                (WorkItemTimelineEntriesTable.workItemId eq workItemId.toKotlinUuid()) and
                WorkItemTimelineEntriesTable.deletedAt.isNull()
            query.cursor?.let { cursor -> condition = condition and cursorBefore(cursor) }
            condition
          }
          .orderBy(WorkItemTimelineEntriesTable.occurredAt to SortOrder.DESC)
          .orderBy(WorkItemTimelineEntriesTable.entryKindRank to SortOrder.DESC)
          .orderBy(WorkItemTimelineEntriesTable.id to SortOrder.DESC)
          .limit(fetchLimit)
          .map { it.toTimelineRow() }
      val pageRows = rows.take(limit)
      val nextCursor =
        if (rows.size > limit) {
          val last = pageRows.last()
          WorkbenchCursor(
            occurredAt = last.occurredAt,
            entryKind = last.entryKind,
            entryId = last.entryId,
          )
        } else {
          null
        }
      WorkItemTimelinePage(
        items = pageRows.mapNotNull { it.hydrate() },
        nextCursor = nextCursor,
      )
    }

  private fun cursorBefore(cursor: WorkbenchCursor) =
    (WorkItemTimelineEntriesTable.occurredAt less cursor.occurredAt) or
      ((WorkItemTimelineEntriesTable.occurredAt eq cursor.occurredAt) and
        (WorkItemTimelineEntriesTable.entryKindRank less cursor.entryKind.sortRank.toShort())) or
      ((WorkItemTimelineEntriesTable.occurredAt eq cursor.occurredAt) and
        (WorkItemTimelineEntriesTable.entryKindRank eq cursor.entryKind.sortRank.toShort()) and
        (WorkItemTimelineEntriesTable.id less cursor.entryId.toKotlinUuid()))

  private fun TimelineRow.hydrate(): WorkItemTimelineEntry? =
    when (entryKind) {
      WorkbenchTimelineEntryKind.ACTIVITY ->
        activities.loadRecord(sourceId)?.let(WorkItemTimelineEntry::Activity)
      WorkbenchTimelineEntryKind.COMMENT ->
        comments.loadRecord(sourceId)?.let(WorkItemTimelineEntry::Comment)
    }

  private fun ResultRow.toTimelineRow(): TimelineRow =
    TimelineRow(
      entryId = this[WorkItemTimelineEntriesTable.id].toJavaUuid(),
      entryKind =
        WorkbenchTimelineEntryKind.fromWireName(this[WorkItemTimelineEntriesTable.entryKind])
          ?: WorkbenchTimelineEntryKind.ACTIVITY,
      sourceId = this[WorkItemTimelineEntriesTable.sourceId].toJavaUuid(),
      occurredAt = this[WorkItemTimelineEntriesTable.occurredAt],
    )

  private fun resolveWorkItemId(tenantId: UUID, projectId: UUID, workItemApiId: String): UUID =
    IssuesTable.selectAll()
      .where {
        (IssuesTable.tenantId eq tenantId.toKotlinUuid()) and
          (IssuesTable.projectId eq projectId.toKotlinUuid()) and
          (IssuesTable.apiId eq workItemApiId) and
          IssuesTable.archivedAt.isNull() and
          IssuesTable.deletedAt.isNull()
      }
      .singleOrNull()
      ?.get(IssuesTable.id)
      ?.toJavaUuid()
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND)

  private data class TimelineRow(
    val entryId: UUID,
    val entryKind: WorkbenchTimelineEntryKind,
    val sourceId: UUID,
    val occurredAt: java.time.OffsetDateTime,
  )

  private companion object {
    const val MIN_LIMIT = 1
    const val MAX_LIMIT = 100
  }
}
