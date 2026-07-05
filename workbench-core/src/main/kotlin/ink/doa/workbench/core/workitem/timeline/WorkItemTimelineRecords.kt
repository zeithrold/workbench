package ink.doa.workbench.core.workitem.timeline

import ink.doa.workbench.core.common.pagination.WorkbenchCursor
import ink.doa.workbench.core.workitem.activity.WorkItemActivityRecord
import ink.doa.workbench.core.workitem.model.WorkItemCommentRecord
import java.util.UUID

data class ListWorkItemTimelineQuery(
  val tenantId: UUID,
  val projectId: UUID,
  val workItemApiId: String,
  val limit: Int = 50,
  val cursor: WorkbenchCursor? = null,
)

sealed interface WorkItemTimelineEntry {
  data class Activity(val record: WorkItemActivityRecord) : WorkItemTimelineEntry

  data class Comment(val record: WorkItemCommentRecord) : WorkItemTimelineEntry
}

data class WorkItemTimelinePage(
  val items: List<WorkItemTimelineEntry>,
  val nextCursor: WorkbenchCursor?,
)
