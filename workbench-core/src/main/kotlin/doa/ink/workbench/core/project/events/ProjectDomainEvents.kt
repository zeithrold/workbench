package doa.ink.workbench.core.project.events

import doa.ink.workbench.core.messaging.DomainEventSpec
import doa.ink.workbench.core.messaging.DomainTopics

object ProjectDomainEvents {
  val DestroyRequested =
    DomainEventSpec(
      type = "project.destroy_requested",
      topic = DomainTopics.PROJECT,
      serializer = ProjectDestroyRequestedEvent.serializer(),
    )

  val Destroyed =
    DomainEventSpec(
      type = "project.destroyed",
      topic = DomainTopics.PROJECT,
      serializer = ProjectDestroyedEvent.serializer(),
    )
}
