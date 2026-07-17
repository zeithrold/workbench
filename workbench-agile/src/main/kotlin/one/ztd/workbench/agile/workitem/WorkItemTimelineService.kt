package one.ztd.workbench.agile.workitem

import java.util.UUID
import one.ztd.workbench.agile.workitem.timeline.ListWorkItemTimelineQuery
import one.ztd.workbench.agile.workitem.timeline.WorkItemTimelinePage
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.kernel.common.pagination.WorkItemStreamCursor
import org.springframework.stereotype.Service

@Service
class WorkItemTimelineService(
  private val timeline: WorkItemTimelineRepository,
  private val workItems: WorkItemRepository,
) {
  suspend fun list(
    tenantId: UUID,
    projectId: UUID,
    workItemApiId: String,
    limit: Int = 50,
    cursorToken: String? = null,
  ): WorkItemTimelinePage {
    requireWorkItem(tenantId, projectId, workItemApiId)
    val cursor = cursorToken?.let(WorkItemStreamCursor::decode)
    return timeline.listByWorkItem(
      ListWorkItemTimelineQuery(
        tenantId = tenantId,
        projectId = projectId,
        workItemApiId = workItemApiId,
        limit = limit,
        cursor = cursor,
      )
    )
  }

  private suspend fun requireWorkItem(tenantId: UUID, projectId: UUID, workItemApiId: String) {
    workItems.findByApiId(tenantId, projectId, workItemApiId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND)
  }
}
