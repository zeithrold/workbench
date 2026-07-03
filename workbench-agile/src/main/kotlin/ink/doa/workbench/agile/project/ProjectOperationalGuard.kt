package ink.doa.workbench.agile.project

import ink.doa.workbench.core.common.errors.ProjectArchivedException
import ink.doa.workbench.core.common.errors.ProjectDestroyingException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.project.ProjectRepository
import ink.doa.workbench.core.project.model.ProjectRecord
import ink.doa.workbench.core.project.model.ProjectStatus
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
