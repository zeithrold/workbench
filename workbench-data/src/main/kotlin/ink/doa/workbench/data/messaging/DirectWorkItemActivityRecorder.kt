package ink.doa.workbench.data.messaging

import ink.doa.workbench.core.port.activity.WorkItemActivityRecorder
import ink.doa.workbench.core.workitem.WorkItemActivityRepository
import ink.doa.workbench.core.workitem.activity.PendingWorkItemActivity
import kotlinx.coroutines.runBlocking

class DirectWorkItemActivityRecorder(private val activities: WorkItemActivityRepository) :
  WorkItemActivityRecorder {
  override fun enqueue(pending: PendingWorkItemActivity, workItemApiId: String) {
    runBlocking { activities.createWithId(pending) }
  }
}
