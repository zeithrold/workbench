package one.ztd.workbench.agile.workitem

import one.ztd.workbench.agile.workitem.timeline.ListWorkItemTimelineQuery
import one.ztd.workbench.agile.workitem.timeline.WorkItemTimelinePage

interface WorkItemTimelineRepository {
  suspend fun listByWorkItem(query: ListWorkItemTimelineQuery): WorkItemTimelinePage
}
