package ink.doa.workbench.application.jobs.messaging

import ink.doa.workbench.agile.project.events.ProjectDestroyRequestedEvent
import ink.doa.workbench.agile.project.events.ProjectDomainEvents
import ink.doa.workbench.kernel.messaging.DomainEventDecoder
import ink.doa.workbench.kernel.messaging.DomainEventEnvelope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@ink.doa.workbench.application.jobs.JobsEnabled
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
