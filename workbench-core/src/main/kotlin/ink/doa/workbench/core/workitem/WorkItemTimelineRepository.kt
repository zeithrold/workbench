package ink.doa.workbench.core.workitem

import ink.doa.workbench.core.workitem.timeline.ListWorkItemTimelineQuery
import ink.doa.workbench.core.workitem.timeline.WorkItemTimelinePage

interface WorkItemTimelineRepository {
  suspend fun listByWorkItem(query: ListWorkItemTimelineQuery): WorkItemTimelinePage
}
