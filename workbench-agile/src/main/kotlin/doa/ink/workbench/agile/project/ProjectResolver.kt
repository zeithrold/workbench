package doa.ink.workbench.agile.project

import doa.ink.workbench.core.common.errors.ResourceNotFoundException
import doa.ink.workbench.core.project.ProjectRepository
import doa.ink.workbench.core.project.model.ProjectRecord
import java.util.UUID
import org.springframework.stereotype.Component

@Component
class ProjectResolver(private val projects: ProjectRepository) {
  suspend fun resolveProject(tenantId: UUID, publicId: String): ProjectRecord =
    projects.findByApiId(tenantId, publicId)
      ?: throw ResourceNotFoundException("Project not found.")
}
