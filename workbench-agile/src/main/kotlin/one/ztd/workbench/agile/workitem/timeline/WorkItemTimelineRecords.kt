package one.ztd.workbench.agile.workitem.timeline

import java.util.UUID
import one.ztd.workbench.agile.workitem.model.WorkItemCommentRecord
import one.ztd.workbench.agile.workitem.stream.WorkItemEventRecord
import one.ztd.workbench.kernel.common.pagination.WorkItemStreamCursor

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
