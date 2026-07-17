package one.ztd.workbench.agile.sprint

import java.util.UUID
import one.ztd.workbench.agile.sprint.events.SprintCloseRequestedEvent

data class SprintCloseRetryRequest(
  val tenantId: UUID,
  val projectId: UUID,
  val sprintApiId: String,
  val operationApiId: String,
  val payload: SprintCloseRequestedEvent,
  val metadataTenantId: String,
)
