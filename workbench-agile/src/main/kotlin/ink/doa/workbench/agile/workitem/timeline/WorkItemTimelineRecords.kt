package ink.doa.workbench.agile.workitem.timeline

import ink.doa.workbench.agile.workitem.model.WorkItemCommentRecord
import ink.doa.workbench.agile.workitem.stream.WorkItemEventRecord
import ink.doa.workbench.kernel.common.pagination.WorkItemStreamCursor
import java.util.UUID

data class ListWorkItemTimelineQuery(
  val tenantId: UUID,
  val projectId: UUID,
  val workItemApiId: String,
  val limit: Int = 50,
  val cursor: WorkItemStreamCursor? = null,
)

sealed interface WorkItemTimelineEntry {
  data class Event(val record: WorkItemEventRecord) : WorkItemTimelineEntry

  data class Comment(
    val event: WorkItemEventRecord,
    val comment: WorkItemCommentRecord,
  ) : WorkItemTimelineEntry
}

data class WorkItemTimelinePage(
  val items: List<WorkItemTimelineEntry>,
  val nextCursor: WorkItemStreamCursor?,
)
