package doa.ink.workbench.agile.project

import doa.ink.workbench.core.project.ProjectRepository
import doa.ink.workbench.core.project.model.CreateProjectCommand
import doa.ink.workbench.core.project.model.ProjectRecord
import doa.ink.workbench.core.project.model.UpdateProjectCommand
import java.util.UUID
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
}
