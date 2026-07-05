package ink.doa.workbench.core.port.activity

import ink.doa.workbench.core.workitem.activity.PendingWorkItemActivity

interface WorkItemActivityRecorder {
  fun enqueue(pending: PendingWorkItemActivity, workItemApiId: String)
}
