package ink.doa.workbench.agile.project

import ink.doa.workbench.agile.project.model.ProjectRecord
import ink.doa.workbench.kernel.common.errors.ResourceNotFoundException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import java.util.UUID
import org.springframework.stereotype.Component

@Component
class ProjectResolver(private val projects: ProjectRepository) {
  suspend fun resolveProject(tenantId: UUID, publicId: String): ProjectRecord =
    projects.findByApiId(tenantId, publicId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PROJECT_NOT_FOUND)
}
