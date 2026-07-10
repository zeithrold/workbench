package ink.doa.workbench.core.workitem.events

import ink.doa.workbench.core.messaging.DomainTopics
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

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
