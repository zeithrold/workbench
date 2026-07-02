package doa.ink.workbench.core.project

import doa.ink.workbench.core.project.model.CreateProjectCommand
import doa.ink.workbench.core.project.model.ProjectRecord
import doa.ink.workbench.core.project.model.UpdateProjectCommand
import java.util.UUID

interface ProjectRepository {
  suspend fun create(command: CreateProjectCommand): ProjectRecord

  suspend fun findByApiId(tenantId: UUID, apiId: String): ProjectRecord?

  suspend fun findById(tenantId: UUID, id: UUID): ProjectRecord?

  suspend fun list(tenantId: UUID, identifier: String? = null): List<ProjectRecord>

  suspend fun update(command: UpdateProjectCommand): ProjectRecord

  suspend fun delete(tenantId: UUID, projectId: UUID): Boolean
}
