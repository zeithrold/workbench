package ink.doa.workbench.agile.workitem

import ink.doa.workbench.agile.workitem.timeline.ListWorkItemTimelineQuery
import ink.doa.workbench.agile.workitem.timeline.WorkItemTimelinePage

interface WorkItemTimelineRepository {
  suspend fun listByWorkItem(query: ListWorkItemTimelineQuery): WorkItemTimelinePage
}
