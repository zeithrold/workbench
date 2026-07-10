package ink.doa.workbench.core.sprint.events

import ink.doa.workbench.core.messaging.DomainTopics
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SprintDomainEventsTest :
  StringSpec({
    "expose sprint lifecycle event specs" {
      SprintDomainEvents.CloseRequested.type shouldBe "sprint.close_requested"
      SprintDomainEvents.CloseRequested.topic shouldBe DomainTopics.SPRINT
      SprintDomainEvents.Closed.type shouldBe "sprint.closed"
      SprintDomainEvents.CloseFailed.type shouldBe "sprint.close_failed"
    }
  })
