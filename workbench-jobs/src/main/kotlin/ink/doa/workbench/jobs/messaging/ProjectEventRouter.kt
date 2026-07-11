package ink.doa.workbench.jobs.messaging

import ink.doa.workbench.core.messaging.DomainEventDecoder
import ink.doa.workbench.core.messaging.DomainEventEnvelope
import ink.doa.workbench.core.project.events.ProjectDestroyRequestedEvent
import ink.doa.workbench.core.project.events.ProjectDomainEvents
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@ink.doa.workbench.jobs.JobsEnabled
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
