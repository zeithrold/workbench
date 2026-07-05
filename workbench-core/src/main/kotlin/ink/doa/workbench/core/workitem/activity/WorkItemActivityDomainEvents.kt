package ink.doa.workbench.core.workitem.activity

import ink.doa.workbench.core.messaging.DomainEventSpec
import ink.doa.workbench.core.messaging.DomainTopics

object WorkItemActivityDomainEvents {
  val RecordRequested =
    DomainEventSpec(
      type = "work_item.activity.record_requested",
      topic = DomainTopics.WORK_ITEM_ACTIVITY,
      serializer = WorkItemActivityRecordRequestedEvent.serializer(),
    )
}
