package one.ztd.workbench.agile.sprint.model

import java.time.OffsetDateTime
import java.util.UUID
import one.ztd.workbench.kernel.common.ids.PublicId

data class SprintRecord(
  val id: UUID,
  val apiId: PublicId,
  val tenantId: UUID,
  val projectId: UUID,
  val name: String,
  val goal: String?,
  val status: SprintStatus,
  val startAt: OffsetDateTime?,
  val endAt: OffsetDateTime?,
  val closedAt: OffsetDateTime?,
  val createdBy: UUID?,
  val archivedAt: OffsetDateTime?,
  val archivedBy: UUID?,
  val deletedAt: OffsetDateTime?,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)

data class CreateSprintCommand(
  val tenantId: UUID,
  val projectId: UUID,
  val name: String,
  val goal: String?,
  val startAt: OffsetDateTime?,
  val endAt: OffsetDateTime?,
  val createdBy: UUID,
)

data class UpdateSprintCommand(
  val tenantId: UUID,
  val projectId: UUID,
  val sprintApiId: String,
  val name: String? = null,
  val goal: String? = null,
  val startAt: OffsetDateTime? = null,
  val endAt: OffsetDateTime? = null,
  val actorUserId: UUID,
)

data class StartSprintCommand(
  val tenantId: UUID,
  val projectId: UUID,
  val sprintApiId: String,
  val actorUserId: UUID,
)

data class CloseSprintCommand(
  val tenantId: UUID,
  val projectId: UUID,
  val sprintApiId: String,
  val actorUserId: UUID,
  val disposition: SprintCloseDisposition = SprintCloseDisposition.KEEP,
  val targetSprintApiId: String? = null,
  val idempotencyKey: String? = null,
)

enum class SprintCloseDisposition {
  BACKLOG,
  NEXT_SPRINT,
  KEEP,
}

enum class SprintCloseOperationStatus {
  QUEUED,
  RUNNING,
  SUCCEEDED,
  FAILED,
}

data class SprintCloseOperationRecord(
  val id: UUID,
  val apiId: PublicId,
  val tenantId: UUID,
  val projectId: UUID,
  val sprintId: UUID,
  val sprintApiId: PublicId,
  val targetSprintId: UUID?,
  val targetSprintApiId: PublicId?,
  val disposition: SprintCloseDisposition,
  val requestedBy: UUID,
  val status: SprintCloseOperationStatus,
  val totalItems: Int,
  val processedItems: Int,
  val failedItems: Int,
  val lastError: String?,
  val idempotencyKey: String?,
  val createdAt: OffsetDateTime,
  val startedAt: OffsetDateTime?,
  val completedAt: OffsetDateTime?,
)

data class ArchiveSprintCommand(
  val tenantId: UUID,
  val projectId: UUID,
  val sprintApiId: String,
  val actorUserId: UUID,
)

data class DeleteSprintCommand(
  val tenantId: UUID,
  val projectId: UUID,
  val sprintApiId: String,
  val actorUserId: UUID,
  val deleteReason: String? = null,
)
