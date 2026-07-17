package one.ztd.workbench.agile.workitem.events

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import one.ztd.workbench.kernel.messaging.DomainTopics

class WorkItemSprintDomainEventsTest :
  StringSpec({
    "expose sprint changed event spec" {
      WorkItemSprintDomainEvents.SprintChanged.type shouldBe "work_item.sprint_changed"
      WorkItemSprintDomainEvents.SprintChanged.topic shouldBe DomainTopics.WORK_ITEM
    }
  })
