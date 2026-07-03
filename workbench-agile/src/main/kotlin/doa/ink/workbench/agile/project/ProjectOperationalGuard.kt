package doa.ink.workbench.agile.project

import doa.ink.workbench.core.common.errors.ProjectArchivedException
import doa.ink.workbench.core.common.errors.ProjectDestroyingException
import doa.ink.workbench.core.common.errors.ResourceNotFoundException
import doa.ink.workbench.core.project.ProjectRepository
import doa.ink.workbench.core.project.model.ProjectRecord
import doa.ink.workbench.core.project.model.ProjectStatus
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class ProjectOperationalGuard(private val projects: ProjectRepository) {
  suspend fun ensureOperational(tenantId: UUID, projectId: UUID): ProjectRecord {
    val project =
      projects.findById(tenantId, projectId)
        ?: throw ResourceNotFoundException("Project not found.")
    if (project.status == ProjectStatus.DESTROYING) {
      throw ProjectDestroyingException("Project is being destroyed.")
    }
    return project
  }

  suspend fun ensureWritable(tenantId: UUID, projectId: UUID): ProjectRecord {
    val project = ensureOperational(tenantId, projectId)
    if (project.status == ProjectStatus.ARCHIVED) {
      throw ProjectArchivedException("Project is archived.")
    }
    return project
  }
}
