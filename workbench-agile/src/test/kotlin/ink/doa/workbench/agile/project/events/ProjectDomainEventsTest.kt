package ink.doa.workbench.agile.project.events

import ink.doa.workbench.kernel.messaging.DomainTopics
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

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
