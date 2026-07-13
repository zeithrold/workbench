package ink.doa.workbench.data.repository.workitem

import ink.doa.workbench.agile.workitem.WorkItemTimelineRepository
import ink.doa.workbench.agile.workitem.activity.WorkItemActivityPayload
import ink.doa.workbench.agile.workitem.stream.WorkItemEventType
import ink.doa.workbench.agile.workitem.timeline.ListWorkItemTimelineQuery
import ink.doa.workbench.agile.workitem.timeline.WorkItemTimelineEntry
import ink.doa.workbench.agile.workitem.timeline.WorkItemTimelinePage
import ink.doa.workbench.data.persistence.postgres.workitem.IssuesTable
import ink.doa.workbench.data.persistence.postgres.workitem.WorkItemTimelineEntriesTable
import ink.doa.workbench.kernel.common.errors.ResourceNotFoundException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import ink.doa.workbench.kernel.common.pagination.WorkItemStreamCursor
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.springframework.stereotype.Repository

@Repository
class ExposedWorkItemTimelineRepository(
  private val database: Database,
  private val events: ExposedWorkItemEventRepository,
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
          .orderBy(WorkItemTimelineEntriesTable.sequence to SortOrder.DESC)
          .limit(fetchLimit)
          .map { it.toTimelineRow() }
      val pageRows = rows.take(limit)
      val nextCursor =
        if (rows.size > limit) {
          val last = pageRows.last()
          WorkItemStreamCursor(sequence = last.sequence)
        } else {
          null
        }
      WorkItemTimelinePage(
        items = pageRows.mapNotNull { it.hydrate(query.tenantId) },
        nextCursor = nextCursor,
      )
    }

  private fun cursorBefore(cursor: WorkItemStreamCursor) =
    WorkItemTimelineEntriesTable.sequence less cursor.sequence

  private fun TimelineRow.hydrate(tenantId: UUID): WorkItemTimelineEntry? {
    val event = events.loadRecord(eventId) ?: return null
    return when (event.eventType) {
      WorkItemEventType.COMMENT_ADDED,
      WorkItemEventType.COMMENT_EDITED -> hydrateCommentEntry(event, tenantId)
      WorkItemEventType.COMMENT_DELETED -> null
      else -> WorkItemTimelineEntry.Event(event)
    }
  }

  private fun hydrateCommentEntry(
    event: ink.doa.workbench.agile.workitem.stream.WorkItemEventRecord,
    tenantId: UUID,
  ): WorkItemTimelineEntry? {
    val commentApiId = event.commentApiId() ?: return null
    val comment = comments.loadByApiId(tenantId, event.workItemId, commentApiId) ?: return null
    return WorkItemTimelineEntry.Comment(event = event, comment = comment)
  }

  private fun ResultRow.toTimelineRow(): TimelineRow =
    TimelineRow(
      eventId = this[WorkItemTimelineEntriesTable.eventId].toJavaUuid(),
      sequence = this[WorkItemTimelineEntriesTable.sequence],
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
    val eventId: UUID,
    val sequence: Long,
  )

  private companion object {
    const val MIN_LIMIT = 1
    const val MAX_LIMIT = 100
  }
}

private fun ink.doa.workbench.agile.workitem.stream.WorkItemEventRecord.commentApiId(): String? =
  when (val payload = payload) {
    is WorkItemActivityPayload.CommentAdded -> payload.value.comment.id
    is WorkItemActivityPayload.CommentEdited -> payload.value.comment.id
    else -> null
  }
