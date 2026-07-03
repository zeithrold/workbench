package doa.ink.workbench.worker.messaging

import doa.ink.workbench.core.messaging.DomainEventDecoder
import doa.ink.workbench.core.messaging.DomainEventEnvelope
import doa.ink.workbench.core.project.events.ProjectDestroyRequestedEvent
import doa.ink.workbench.core.project.events.ProjectDomainEvents
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ProjectEventRouter(
  private val decoder: DomainEventDecoder,
  private val projectDestroyRequestedHandler: DomainEventHandler<ProjectDestroyRequestedEvent>,
) {
  private val logger = LoggerFactory.getLogger(javaClass)

  suspend fun route(envelope: DomainEventEnvelope) {
    when (envelope.type) {
      ProjectDomainEvents.DestroyRequested.type -> {
        val event = decoder.decode(ProjectDomainEvents.DestroyRequested, envelope)
        projectDestroyRequestedHandler.handle(event)
      }
      else ->
        logger.warn(
          "project_event_skipped type={} eventId={} reason=unknown_type",
          envelope.type,
          envelope.eventId,
        )
    }
  }
}
