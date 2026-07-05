package ink.doa.workbench.core.sprint

import ink.doa.workbench.core.sprint.model.ArchiveSprintCommand
import ink.doa.workbench.core.sprint.model.CreateSprintCommand
import ink.doa.workbench.core.sprint.model.DeleteSprintCommand
import ink.doa.workbench.core.sprint.model.SprintRecord
import ink.doa.workbench.core.sprint.model.SprintStatus
import ink.doa.workbench.core.sprint.model.UpdateSprintCommand
import java.time.OffsetDateTime
import java.util.UUID

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

  suspend fun markArchived(command: ArchiveSprintCommand): SprintRecord

  suspend fun softDelete(command: DeleteSprintCommand): Boolean

  suspend fun countActiveByProject(
    tenantId: UUID,
    projectId: UUID,
    excludingSprintId: UUID? = null,
  ): Long
}
