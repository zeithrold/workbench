package one.ztd.workbench.agile.project

import java.util.UUID
import one.ztd.workbench.agile.project.model.ProjectRecord
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import org.springframework.stereotype.Component

@Component
class ProjectResolver(private val projects: ProjectRepository) {
  suspend fun resolveProject(tenantId: UUID, publicId: String): ProjectRecord =
    projects.findByApiId(tenantId, publicId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PROJECT_NOT_FOUND)
}
