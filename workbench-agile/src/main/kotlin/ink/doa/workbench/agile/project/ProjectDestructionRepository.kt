package ink.doa.workbench.agile.project

import java.time.OffsetDateTime
import java.util.UUID

interface ProjectDestructionRepository {
  suspend fun expireBindingsByProject(
    tenantId: UUID,
    projectId: UUID,
    expiredAt: OffsetDateTime,
  ): Int

  suspend fun softDeleteProjectScopedData(
    tenantId: UUID,
    projectId: UUID,
    deletedAt: OffsetDateTime,
    deletedBy: UUID,
    deleteReason: String?,
  )
}
