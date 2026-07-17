package one.ztd.workbench.agile.sprint

import java.time.OffsetDateTime
import java.util.UUID
import one.ztd.workbench.agile.sprint.model.ArchiveSprintCommand
import one.ztd.workbench.agile.sprint.model.CreateSprintCommand
import one.ztd.workbench.agile.sprint.model.DeleteSprintCommand
import one.ztd.workbench.agile.sprint.model.SprintRecord
import one.ztd.workbench.agile.sprint.model.SprintStatus
import one.ztd.workbench.agile.sprint.model.UpdateSprintCommand

interface SprintRepository {
  suspend fun list(
    tenantId: UUID,
    projectId: UUID,
    status: SprintStatus? = null,
  ): List<SprintRecord>

  suspend fun findByApiId(
    tenantId: UUID,
    projectId: UUID,
    sprintApiId: String,
  ): SprintRecord?

  suspend fun create(command: CreateSprintCommand): SprintRecord

  suspend fun update(command: UpdateSprintCommand): SprintRecord

  suspend fun markActive(
    tenantId: UUID,
    projectId: UUID,
    sprintApiId: String,
    startAt: OffsetDateTime,
    actorUserId: UUID,
  ): SprintRecord

  suspend fun markClosed(
    tenantId: UUID,
    projectId: UUID,
    sprintApiId: String,
    closedAt: OffsetDateTime,
    actorUserId: UUID,
  ): SprintRecord

  suspend fun markClosing(
    tenantId: UUID,
    projectId: UUID,
    sprintApiId: String,
    actorUserId: UUID,
  ): SprintRecord

  suspend fun markClosedFromClosing(
    tenantId: UUID,
    projectId: UUID,
    sprintApiId: String,
    closedAt: OffsetDateTime,
    actorUserId: UUID,
  ): SprintRecord

  suspend fun markArchived(command: ArchiveSprintCommand): SprintRecord

  suspend fun softDelete(command: DeleteSprintCommand): Boolean

  suspend fun countActiveByProject(
    tenantId: UUID,
    projectId: UUID,
    excludingSprintId: UUID? = null,
  ): Long
}
