package ink.doa.workbench.agile.sprint

import ink.doa.workbench.agile.sprint.events.SprintCloseRequestedEvent
import java.util.UUID

data class SprintCloseRetryRequest(
  val tenantId: UUID,
  val projectId: UUID,
  val sprintApiId: String,
  val operationApiId: String,
  val payload: SprintCloseRequestedEvent,
  val metadataTenantId: String,
)
