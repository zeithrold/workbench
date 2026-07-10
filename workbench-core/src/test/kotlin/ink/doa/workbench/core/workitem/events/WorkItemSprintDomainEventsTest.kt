package ink.doa.workbench.core.workitem.events

import ink.doa.workbench.core.messaging.DomainTopics
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class WorkItemSprintDomainEventsTest :
  StringSpec({
    "expose sprint changed event spec" {
      WorkItemSprintDomainEvents.SprintChanged.type shouldBe "work_item.sprint_changed"
      WorkItemSprintDomainEvents.SprintChanged.topic shouldBe DomainTopics.WORK_ITEM
    }
  })
