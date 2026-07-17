package one.ztd.workbench.agile.workitem

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.UUID
import one.ztd.workbench.agile.sprint.model.SprintCloseDisposition
import one.ztd.workbench.agile.workitem.model.WorkItemRecord

class ReassignSprintBatchModelsTest :
  StringSpec({
    "reassign sprint batch command stores defaults and fields" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val sourceSprintId = UUID.randomUUID()

      ReassignSprintBatchCommand(
          tenantId = tenantId,
          projectId = projectId,
          sourceSprintId = sourceSprintId,
          targetSprintId = null,
          disposition = SprintCloseDisposition.BACKLOG,
          actorUserId = UUID.randomUUID(),
          operationId = "sop_1",
        )
        .limit shouldBe 100
    }

    "reassign sprint batch result stores changed items" {
      ReassignSprintBatchResult(
          processedItems = 2,
          remainingItems = 1,
          changedItems = emptyList<WorkItemRecord>(),
        )
        .remainingItems shouldBe 1
    }
  })
