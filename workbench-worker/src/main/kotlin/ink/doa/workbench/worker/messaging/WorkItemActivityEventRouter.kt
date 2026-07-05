package ink.doa.workbench.worker.messaging

import ink.doa.workbench.core.messaging.DomainEventDecoder
import ink.doa.workbench.core.messaging.DomainEventEnvelope
import ink.doa.workbench.core.workitem.activity.WorkItemActivityDomainEvents
import ink.doa.workbench.core.workitem.activity.WorkItemActivityRecordRequestedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class WorkItemActivityEventRouter(
  private val decoder: DomainEventDecoder,
  private val recordRequestedHandler: DomainEventHandler<WorkItemActivityRecordRequestedEvent>,
) {
  private val logger = LoggerFactory.getLogger(javaClass)

  suspend fun route(envelope: DomainEventEnvelope) {
    when (envelope.type) {
      WorkItemActivityDomainEvents.RecordRequested.type -> {
        val event = decoder.decode(WorkItemActivityDomainEvents.RecordRequested, envelope)
        recordRequestedHandler.handle(event)
      }
      else ->
        logger.warn(
          "work_item_activity_event_skipped type={} eventId={} reason=unknown_type",
          envelope.type,
          envelope.eventId,
        )
    }
  }
}
