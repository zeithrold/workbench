package ink.doa.workbench.data.persistence.postgres.workitem

import ink.doa.workbench.core.common.pagination.WorkbenchTimelineEntryKind
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.update

internal data class InsertedWorkItemTimelineEntry(val id: UUID)

internal data class InsertTimelineEntryCommand(
  val tenantId: UUID,
  val projectId: UUID,
  val workItemId: UUID,
  val entryKind: WorkbenchTimelineEntryKind,
  val sourceId: UUID,
  val occurredAt: OffsetDateTime,
  val createdAt: OffsetDateTime = occurredAt,
)

internal fun insertTimelineEntry(
  command: InsertTimelineEntryCommand
): InsertedWorkItemTimelineEntry {
  val entryId = UUID.randomUUID()
  WorkItemTimelineEntriesTable.insert {
    it[id] = entryId.toKotlinUuid()
    it[WorkItemTimelineEntriesTable.tenantId] = command.tenantId.toKotlinUuid()
    it[WorkItemTimelineEntriesTable.projectId] = command.projectId.toKotlinUuid()
    it[WorkItemTimelineEntriesTable.workItemId] = command.workItemId.toKotlinUuid()
    it[WorkItemTimelineEntriesTable.entryKind] = command.entryKind.wireName
    it[WorkItemTimelineEntriesTable.entryKindRank] = command.entryKind.sortRank.toShort()
    it[WorkItemTimelineEntriesTable.sourceId] = command.sourceId.toKotlinUuid()
    it[WorkItemTimelineEntriesTable.occurredAt] = command.occurredAt
    it[WorkItemTimelineEntriesTable.createdAt] = command.createdAt
  }
  return InsertedWorkItemTimelineEntry(id = entryId)
}

internal fun softDeleteTimelineEntryByComment(
  tenantId: UUID,
  commentId: UUID,
  deletedAt: OffsetDateTime,
) {
  WorkItemTimelineEntriesTable.update({
    (WorkItemTimelineEntriesTable.tenantId eq tenantId.toKotlinUuid()) and
      (WorkItemTimelineEntriesTable.entryKind eq WorkbenchTimelineEntryKind.COMMENT.wireName) and
      (WorkItemTimelineEntriesTable.sourceId eq commentId.toKotlinUuid()) and
      WorkItemTimelineEntriesTable.deletedAt.isNull()
  }) {
    it[WorkItemTimelineEntriesTable.deletedAt] = deletedAt
  }
}
