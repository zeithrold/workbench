package one.ztd.workbench.application.jobs.messaging

import one.ztd.workbench.agile.project.events.ProjectDestroyRequestedEvent
import one.ztd.workbench.agile.project.events.ProjectDomainEvents
import one.ztd.workbench.kernel.messaging.DomainEventDecoder
import one.ztd.workbench.kernel.messaging.DomainEventEnvelope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@one.ztd.workbench.application.jobs.JobsEnabled
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
