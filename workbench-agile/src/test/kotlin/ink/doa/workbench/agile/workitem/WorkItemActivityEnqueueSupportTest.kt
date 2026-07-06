package ink.doa.workbench.agile.workitem

import ink.doa.workbench.agile.testfixtures.AgileWorkItemFixtures
import ink.doa.workbench.core.port.activity.WorkItemActivityRecorder
import ink.doa.workbench.core.workitem.activity.PendingWorkItemActivity
import ink.doa.workbench.core.workitem.model.WorkItemMutationResult
import io.kotest.core.spec.style.StringSpec
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID

class WorkItemActivityEnqueueSupportTest :
  StringSpec({
    val recorder = mockk<WorkItemActivityRecorder>(relaxed = true)
    val support = WorkItemActivityEnqueueSupport(recorder)

    "enqueue skips recorder when pending activity is null" {
      support.enqueue(null as PendingWorkItemActivity?, "iss_abc")

      verify(exactly = 0) { recorder.enqueue(any(), any()) }
    }

    "enqueue forwards pending activity and work item api id" {
      val pending = mockk<PendingWorkItemActivity>()

      support.enqueue(pending, "iss_target")

      verify(exactly = 1) { recorder.enqueue(pending, "iss_target") }
    }

    "enqueue mutation result uses work item api id" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val actorId = UUID.randomUUID()
      val config = AgileWorkItemFixtures.sampleConfig(tenantId)
      val workItem = AgileWorkItemFixtures.sampleIssue(tenantId, projectId, config, actorId)
      val pending = mockk<PendingWorkItemActivity>()
      val result = WorkItemMutationResult(workItem, "work_item.updated", pendingActivity = pending)

      support.enqueue(result)

      verify(exactly = 1) { recorder.enqueue(pending, workItem.apiId.value) }
    }
  })
