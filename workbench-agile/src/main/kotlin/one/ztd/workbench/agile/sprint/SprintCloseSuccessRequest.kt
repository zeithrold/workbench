package one.ztd.workbench.agile.sprint

import java.time.OffsetDateTime
import java.util.UUID

data class SprintCloseSuccessRequest(
  val operationId: UUID,
  val tenantId: UUID,
  val projectId: UUID,
  val sprintApiId: String,
  val operationApiId: String,
  val closedAt: OffsetDateTime,
  val actorUserId: UUID,
)
