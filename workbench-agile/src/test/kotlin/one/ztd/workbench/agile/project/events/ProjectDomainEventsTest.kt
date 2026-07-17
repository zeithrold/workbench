package one.ztd.workbench.agile.project.events

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import one.ztd.workbench.kernel.messaging.DomainTopics

class ProjectDomainEventsTest :
  StringSpec({
    "destroy requested event spec uses project topic" {
      ProjectDomainEvents.DestroyRequested.type shouldBe "project.destroy_requested"
      ProjectDomainEvents.DestroyRequested.topic shouldBe DomainTopics.PROJECT
    }

    "destroyed event spec uses project topic" {
      ProjectDomainEvents.Destroyed.type shouldBe "project.destroyed"
      ProjectDomainEvents.Destroyed.topic shouldBe DomainTopics.PROJECT
    }
  })
