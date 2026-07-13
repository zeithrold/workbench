package ink.doa.workbench.agile.workitem.events

import ink.doa.workbench.kernel.messaging.DomainEventSpec
import ink.doa.workbench.kernel.messaging.DomainTopics

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
