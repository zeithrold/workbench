package ink.doa.workbench.core.workitem

import ink.doa.workbench.core.workitem.activity.CreateWorkItemActivityCommand
import ink.doa.workbench.core.workitem.activity.ListWorkItemActivitiesQuery
import ink.doa.workbench.core.workitem.activity.PendingWorkItemActivity
import ink.doa.workbench.core.workitem.activity.WorkItemActivityListPage
import ink.doa.workbench.core.workitem.activity.WorkItemActivityRecord

interface WorkItemActivityRepository {
  suspend fun <T : Any> create(command: CreateWorkItemActivityCommand<T>): WorkItemActivityRecord

  suspend fun createWithId(pending: PendingWorkItemActivity): WorkItemActivityRecord

  suspend fun createAll(
    commands: List<CreateWorkItemActivityCommand<*>>
  ): List<WorkItemActivityRecord>

  suspend fun listByWorkItem(query: ListWorkItemActivitiesQuery): WorkItemActivityListPage
}
