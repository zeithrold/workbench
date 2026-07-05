package ink.doa.workbench.data.messaging

import ink.doa.workbench.core.messaging.EventMetadata
import ink.doa.workbench.core.port.activity.WorkItemActivityRecorder
import ink.doa.workbench.core.port.messaging.DomainEventPublisher
import ink.doa.workbench.core.workitem.activity.PendingWorkItemActivity
import ink.doa.workbench.core.workitem.activity.WorkItemActivityCodec
import ink.doa.workbench.core.workitem.activity.WorkItemActivityDomainEvents
import ink.doa.workbench.core.workitem.activity.WorkItemActivityRecordRequestedEvent
import org.springframework.stereotype.Component

@Component
class KafkaWorkItemActivityRecorder(
  private val events: DomainEventPublisher,
  private val codec: WorkItemActivityCodec,
) : WorkItemActivityRecorder {
  override fun enqueue(pending: PendingWorkItemActivity, workItemApiId: String) {
    events.publish(
      spec = WorkItemActivityDomainEvents.RecordRequested,
      key = workItemApiId,
      payload = WorkItemActivityRecordRequestedEvent.from(pending, codec),
      metadata = EventMetadata(tenantId = pending.command.tenantId.toString()),
    )
  }
}
