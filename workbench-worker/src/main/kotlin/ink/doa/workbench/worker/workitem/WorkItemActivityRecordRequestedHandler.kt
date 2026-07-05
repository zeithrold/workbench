package ink.doa.workbench.worker.workitem

import ink.doa.workbench.core.workitem.WorkItemActivityRepository
import ink.doa.workbench.core.workitem.activity.WorkItemActivityCodec
import ink.doa.workbench.core.workitem.activity.WorkItemActivityRecordRequestedEvent
import ink.doa.workbench.worker.messaging.DomainEventHandler
import org.springframework.stereotype.Component

@Component
class WorkItemActivityRecordRequestedHandler(
  private val activities: WorkItemActivityRepository,
  private val codec: WorkItemActivityCodec,
) : DomainEventHandler<WorkItemActivityRecordRequestedEvent> {
  override suspend fun handle(payload: WorkItemActivityRecordRequestedEvent) {
    val pending = WorkItemActivityRecordRequestedEvent.toPending(payload, codec)
    activities.createWithId(pending)
  }
}
