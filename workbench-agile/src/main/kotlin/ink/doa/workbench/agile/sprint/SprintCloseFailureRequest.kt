package ink.doa.workbench.agile.sprint

import java.time.OffsetDateTime
import java.util.UUID

data class SprintCloseFailureRequest(
  val operationId: UUID,
  val tenantId: UUID,
  val projectId: UUID,
  val sprintApiId: String,
  val operationApiId: String,
  val error: String,
  val completedAt: OffsetDateTime,
)
