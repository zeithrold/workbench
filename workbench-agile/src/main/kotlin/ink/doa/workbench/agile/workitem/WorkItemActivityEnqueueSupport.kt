package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.port.activity.WorkItemActivityRecorder
import ink.doa.workbench.core.workitem.activity.PendingWorkItemActivity
import ink.doa.workbench.core.workitem.model.WorkItemMutationResult
import org.springframework.stereotype.Component

@Component
class WorkItemActivityEnqueueSupport(private val recorder: WorkItemActivityRecorder) {
  fun enqueue(pending: PendingWorkItemActivity?, workItemApiId: String) {
    pending?.let { recorder.enqueue(it, workItemApiId) }
  }

  fun enqueue(result: WorkItemMutationResult) {
    enqueue(result.pendingActivity, result.workItem.apiId.value)
  }
}
