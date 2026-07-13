package ink.doa.workbench.application.jobs.messaging

import ink.doa.workbench.agile.project.events.ProjectDomainEvents
import ink.doa.workbench.agile.sprint.events.SprintDomainEvents
import ink.doa.workbench.agile.workitem.events.WorkItemDomainEvents
import ink.doa.workbench.application.jobs.notification.WorkItemNotificationHandler
import ink.doa.workbench.application.jobs.sprint.SprintCloseRequestedEventHandler
import ink.doa.workbench.kernel.messaging.DomainEventDecoder
import ink.doa.workbench.kernel.messaging.DomainEventEnvelope
import ink.doa.workbench.kernel.messaging.DomainTopics
import ink.doa.workbench.tenant.tenant.events.TenantDomainEvents
import org.springframework.stereotype.Component

@ink.doa.workbench.application.jobs.JobsEnabled
@Component
class TenantJobRegistration(private val router: TenantEventRouter) : JobRegistration {
  override val consumerName = "tenant-jobs"
  override val topic = DomainTopics.TENANT
  override val eventTypes =
    setOf(TenantDomainEvents.Created.type, TenantDomainEvents.DestroyRequested.type)

  override suspend fun handle(envelope: DomainEventEnvelope) = router.route(envelope)
}

@ink.doa.workbench.application.jobs.JobsEnabled
@Component
class ProjectJobRegistration(private val router: ProjectEventRouter) : JobRegistration {
  override val consumerName = "project-jobs"
  override val topic = DomainTopics.PROJECT
  override val eventTypes = setOf(ProjectDomainEvents.DestroyRequested.type)

  override suspend fun handle(envelope: DomainEventEnvelope) = router.route(envelope)
}

@ink.doa.workbench.application.jobs.JobsEnabled
@Component
class SprintJobRegistration(
  private val decoder: DomainEventDecoder,
  private val handler: SprintCloseRequestedEventHandler,
) : JobRegistration {
  override val consumerName = "sprint-jobs"
  override val topic = DomainTopics.SPRINT
  override val eventTypes = setOf(SprintDomainEvents.CloseRequested.type)

  override suspend fun handle(envelope: DomainEventEnvelope) {
    handler.handle(decoder.decode(SprintDomainEvents.CloseRequested, envelope))
  }
}

@ink.doa.workbench.application.jobs.JobsEnabled
@Component
class WorkItemNotificationJobRegistration(
  private val decoder: DomainEventDecoder,
  private val handler: WorkItemNotificationHandler,
) : JobRegistration {
  override val consumerName = WorkItemNotificationHandler.CONSUMER_NAME
  override val topic = DomainTopics.WORK_ITEM
  override val eventTypes =
    setOf(
      WorkItemDomainEvents.Created.type,
      WorkItemDomainEvents.Updated.type,
      WorkItemDomainEvents.Transitioned.type,
    )

  override suspend fun handle(envelope: DomainEventEnvelope) {
    val spec =
      when (envelope.type) {
        WorkItemDomainEvents.Created.type -> WorkItemDomainEvents.Created
        WorkItemDomainEvents.Updated.type -> WorkItemDomainEvents.Updated
        else -> WorkItemDomainEvents.Transitioned
      }
    handler.handle(envelope.eventId, envelope.type, decoder.decode(spec, envelope))
  }
}
