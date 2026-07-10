package ink.doa.workbench.core.sprint.events

import ink.doa.workbench.core.messaging.DomainEventSpec
import ink.doa.workbench.core.messaging.DomainTopics
import kotlinx.serialization.Serializable

@Serializable
data class SprintCloseRequestedEvent(
  val tenantId: String,
  val projectId: String,
  val sprintId: String,
  val operationId: String,
  val requestedBy: String,
)

@Serializable
data class SprintClosedEvent(
  val tenantId: String,
  val projectId: String,
  val sprintId: String,
  val operationId: String,
)

@Serializable
data class SprintCloseFailedEvent(
  val tenantId: String,
  val projectId: String,
  val sprintId: String,
  val operationId: String,
  val error: String,
)

object SprintDomainEvents {
  val CloseRequested =
    DomainEventSpec(
      type = "sprint.close_requested",
      topic = DomainTopics.SPRINT,
      serializer = SprintCloseRequestedEvent.serializer(),
    )

  val Closed =
    DomainEventSpec(
      type = "sprint.closed",
      topic = DomainTopics.SPRINT,
      serializer = SprintClosedEvent.serializer(),
    )

  val CloseFailed =
    DomainEventSpec(
      type = "sprint.close_failed",
      topic = DomainTopics.SPRINT,
      serializer = SprintCloseFailedEvent.serializer(),
    )
}
