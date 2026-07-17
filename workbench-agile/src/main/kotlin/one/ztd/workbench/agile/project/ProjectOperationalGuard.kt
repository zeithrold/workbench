package one.ztd.workbench.agile.project

import java.util.UUID
import one.ztd.workbench.agile.project.model.ProjectRecord
import one.ztd.workbench.agile.project.model.ProjectStatus
import one.ztd.workbench.kernel.common.errors.ProjectArchivedException
import one.ztd.workbench.kernel.common.errors.ProjectDestroyingException
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import org.springframework.stereotype.Service

@Service
class ProjectOperationalGuard(private val projects: ProjectRepository) {
  suspend fun ensureOperational(tenantId: UUID, projectId: UUID): ProjectRecord {
    val project =
      projects.findById(tenantId, projectId)
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PROJECT_NOT_FOUND)
    if (project.status == ProjectStatus.DESTROYING) {
      throw ProjectDestroyingException()
    }
    return project
  }

  suspend fun ensureWritable(tenantId: UUID, projectId: UUID): ProjectRecord {
    val project = ensureOperational(tenantId, projectId)
    if (project.status == ProjectStatus.ARCHIVED) {
      throw ProjectArchivedException()
    }
    return project
  }
}
