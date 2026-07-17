package one.ztd.workbench.agile.sprint.events

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import one.ztd.workbench.kernel.messaging.DomainTopics

class SprintDomainEventsTest :
  StringSpec({
    "expose sprint lifecycle event specs" {
      SprintDomainEvents.CloseRequested.type shouldBe "sprint.close_requested"
      SprintDomainEvents.CloseRequested.topic shouldBe DomainTopics.SPRINT
      SprintDomainEvents.Closed.type shouldBe "sprint.closed"
      SprintDomainEvents.CloseFailed.type shouldBe "sprint.close_failed"
    }
  })
