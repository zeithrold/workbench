package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.workitem.WorkItemActivityRepository
import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.activity.ListWorkItemActivitiesQuery
import ink.doa.workbench.core.workitem.activity.WorkItemActivityListPage
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class WorkItemActivityService(
  private val activities: WorkItemActivityRepository,
  private val workItems: WorkItemRepository,
) {
  suspend fun list(
    tenantId: UUID,
    projectId: UUID,
    workItemApiId: String,
    limit: Int = 50,
    before: OffsetDateTime? = null,
  ): WorkItemActivityListPage {
    requireWorkItem(tenantId, projectId, workItemApiId)
    return activities.listByWorkItem(
      ListWorkItemActivitiesQuery(
        tenantId = tenantId,
        projectId = projectId,
        workItemApiId = workItemApiId,
        limit = limit,
        before = before,
      )
    )
  }

  private suspend fun requireWorkItem(tenantId: UUID, projectId: UUID, workItemApiId: String) {
    workItems.findByApiId(tenantId, projectId, workItemApiId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND)
  }
}
