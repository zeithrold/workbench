package doa.ink.workbench.core.project

import doa.ink.workbench.core.project.model.CreateProjectCommand
import doa.ink.workbench.core.project.model.ProjectRecord
import doa.ink.workbench.core.project.model.ProjectStatus
import doa.ink.workbench.core.project.model.UpdateProjectCommand
import java.time.OffsetDateTime
import java.util.UUID

interface ProjectRepository {
  suspend fun create(command: CreateProjectCommand): ProjectRecord

  suspend fun findByApiId(tenantId: UUID, apiId: String): ProjectRecord?

  suspend fun findById(tenantId: UUID, id: UUID): ProjectRecord?

  suspend fun list(tenantId: UUID, identifier: String? = null): List<ProjectRecord>

  suspend fun update(command: UpdateProjectCommand): ProjectRecord

  suspend fun markArchived(
    tenantId: UUID,
    projectId: UUID,
    archivedAt: OffsetDateTime,
    archivedBy: UUID,
  ): ProjectRecord

  suspend fun markActive(tenantId: UUID, projectId: UUID): ProjectRecord

  suspend fun markDestroying(
    tenantId: UUID,
    projectId: UUID,
    deletedBy: UUID,
    deleteReason: String?,
  ): ProjectRecord

  suspend fun finalizeDestroy(
    tenantId: UUID,
    projectId: UUID,
    deletedAt: OffsetDateTime,
    deletedBy: UUID,
    deleteReason: String?,
  ): Boolean

  suspend fun updateStatus(
    tenantId: UUID,
    projectId: UUID,
    status: ProjectStatus,
  ): Boolean
}
