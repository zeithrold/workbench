package ink.doa.workbench.core.sprint.model

import ink.doa.workbench.core.common.ids.PublicId
import java.time.OffsetDateTime
import java.util.UUID

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
