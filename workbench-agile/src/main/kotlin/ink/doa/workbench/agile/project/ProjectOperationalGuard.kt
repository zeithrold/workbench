package ink.doa.workbench.agile.project

import ink.doa.workbench.agile.project.model.ProjectRecord
import ink.doa.workbench.agile.project.model.ProjectStatus
import ink.doa.workbench.kernel.common.errors.ProjectArchivedException
import ink.doa.workbench.kernel.common.errors.ProjectDestroyingException
import ink.doa.workbench.kernel.common.errors.ResourceNotFoundException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import java.util.UUID
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
