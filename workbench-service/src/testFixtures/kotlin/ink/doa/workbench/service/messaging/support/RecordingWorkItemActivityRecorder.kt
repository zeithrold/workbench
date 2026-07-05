package ink.doa.workbench.service.messaging.support

import ink.doa.workbench.core.port.activity.WorkItemActivityRecorder
import ink.doa.workbench.core.workitem.activity.PendingWorkItemActivity

data class EnqueuedWorkItemActivity(
  val pending: PendingWorkItemActivity,
  val workItemApiId: String,
)

class RecordingWorkItemActivityRecorder : WorkItemActivityRecorder {
  private val activities = mutableListOf<EnqueuedWorkItemActivity>()

  val enqueued: List<EnqueuedWorkItemActivity>
    get() = activities.toList()

  override fun enqueue(pending: PendingWorkItemActivity, workItemApiId: String) {
    activities += EnqueuedWorkItemActivity(pending, workItemApiId)
  }

  fun clear() {
    activities.clear()
  }
}
