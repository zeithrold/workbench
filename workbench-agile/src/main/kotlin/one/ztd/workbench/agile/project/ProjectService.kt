package one.ztd.workbench.agile.project

import java.time.OffsetDateTime
import java.util.UUID
import one.ztd.workbench.agile.project.model.CreateProjectCommand
import one.ztd.workbench.agile.project.model.ProjectRecord
import one.ztd.workbench.agile.project.model.ProjectStatus
import one.ztd.workbench.agile.project.model.UpdateProjectCommand
import org.springframework.stereotype.Service

@Service
class ProjectService(
  private val repository: ProjectRepository,
  private val projectResolver: ProjectResolver,
) {
  suspend fun create(command: CreateProjectCommand): ProjectRecord = repository.create(command)

  suspend fun list(tenantId: UUID, identifier: String? = null): List<ProjectRecord> =
    repository.list(tenantId, identifier)

  suspend fun get(tenantId: UUID, projectPublicId: String): ProjectRecord =
    projectResolver.resolveProject(tenantId, projectPublicId)

  suspend fun update(command: UpdateProjectCommand): ProjectRecord = repository.update(command)

  suspend fun archive(
    tenantId: UUID,
    projectId: UUID,
    archivedAt: OffsetDateTime,
    actorUserId: UUID,
  ): ProjectRecord =
    repository.markArchived(
      tenantId = tenantId,
      projectId = projectId,
      archivedAt = archivedAt,
      archivedBy = actorUserId,
    )

  suspend fun unarchive(tenantId: UUID, projectId: UUID): ProjectRecord =
    repository.markActive(tenantId, projectId)

  suspend fun markDestroying(
    tenantId: UUID,
    projectId: UUID,
    actorUserId: UUID,
    deleteReason: String?,
  ): ProjectRecord =
    repository.markDestroying(
      tenantId = tenantId,
      projectId = projectId,
      deletedBy = actorUserId,
      deleteReason = deleteReason,
    )

  suspend fun requestDestroy(request: ProjectDestroyRequest): ProjectRecord =
    repository.requestDestroy(request)

  suspend fun restoreStatus(
    tenantId: UUID,
    projectId: UUID,
    status: ProjectStatus,
  ): Boolean = repository.updateStatus(tenantId, projectId, status)
}
