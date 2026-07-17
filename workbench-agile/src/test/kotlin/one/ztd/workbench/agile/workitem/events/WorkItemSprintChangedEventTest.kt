package one.ztd.workbench.agile.workitem.events

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import one.ztd.workbench.kernel.messaging.DomainTopics

class WorkItemSprintChangedEventTest :
  StringSpec({
    "sprint changed event specification uses the work item topic" {
      WorkItemSprintDomainEvents.SprintChanged.type shouldBe "work_item.sprint_changed"
      WorkItemSprintDomainEvents.SprintChanged.topic shouldBe DomainTopics.WORK_ITEM
    }

    "sprint changed event retains optional target sprint" {
      val event =
        WorkItemSprintChangedEvent(
          tenantId = "ten",
          projectId = "prj",
          workItemId = "wki",
          sourceSprintId = "spr_old",
          targetSprintId = null,
          disposition = "BACKLOG",
          operationId = "op",
          actorUserId = "usr",
        )

      event.targetSprintId shouldBe null
      event.disposition shouldBe "BACKLOG"
    }
  })
