package one.ztd.workbench.agile.sprint.events

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import one.ztd.workbench.kernel.messaging.DomainTopics

class SprintCloseEventsTest :
  StringSpec({
    "sprint close event specifications expose stable types and topic" {
      SprintDomainEvents.CloseRequested.type shouldBe "sprint.close_requested"
      SprintDomainEvents.CloseRequested.topic shouldBe DomainTopics.SPRINT
      SprintDomainEvents.Closed.type shouldBe "sprint.closed"
      SprintDomainEvents.CloseFailed.type shouldBe "sprint.close_failed"
    }

    "sprint close event models retain request and failure details" {
      SprintCloseRequestedEvent("ten", "prj", "spr", "op", "usr").requestedBy shouldBe "usr"
      SprintClosedEvent("ten", "prj", "spr", "op").operationId shouldBe "op"
      SprintCloseFailedEvent("ten", "prj", "spr", "op", "failed").error shouldBe "failed"
    }
  })
