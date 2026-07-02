package doa.ink.workbench.service.project

import doa.ink.workbench.core.common.errors.ResourceNotFoundException
import doa.ink.workbench.core.project.ProjectRepository
import doa.ink.workbench.core.project.model.CreateProjectCommand
import doa.ink.workbench.core.project.model.ProjectRecord
import doa.ink.workbench.core.project.model.UpdateProjectCommand
import doa.ink.workbench.service.common.PublicIdResolver
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class ProjectService(
  private val repository: ProjectRepository,
  private val publicIds: PublicIdResolver,
) {
  suspend fun create(command: CreateProjectCommand): ProjectRecord = repository.create(command)

  suspend fun list(tenantId: UUID, identifier: String? = null): List<ProjectRecord> =
    repository.list(tenantId, identifier)

  suspend fun get(tenantId: UUID, projectPublicId: String): ProjectRecord =
    publicIds.resolveProject(tenantId, projectPublicId)

  suspend fun update(command: UpdateProjectCommand): ProjectRecord = repository.update(command)

  suspend fun delete(tenantId: UUID, projectPublicId: String) {
    val project = publicIds.resolveProject(tenantId, projectPublicId)
    if (!repository.delete(tenantId, project.id)) {
      throw ResourceNotFoundException("Project not found.")
    }
  }
}
