package ink.doa.workbench.core.workitem.events

import ink.doa.workbench.core.messaging.DomainEventSpec
import ink.doa.workbench.core.messaging.DomainTopics

object WorkItemDomainEvents {
  val Created =
    DomainEventSpec(
      type = "work_item.created",
      topic = DomainTopics.WORK_ITEM,
      serializer = WorkItemMutationEvent.serializer(),
    )

  val Updated =
    DomainEventSpec(
      type = "work_item.updated",
      topic = DomainTopics.WORK_ITEM,
      serializer = WorkItemMutationEvent.serializer(),
    )

  val Transitioned =
    DomainEventSpec(
      type = "work_item.transitioned",
      topic = DomainTopics.WORK_ITEM,
      serializer = WorkItemMutationEvent.serializer(),
    )
}
