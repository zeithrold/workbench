package doa.ink.workbench.core.project

import doa.ink.workbench.core.project.model.CreateProjectCommand
import doa.ink.workbench.core.project.model.ProjectRecord
import java.util.UUID

interface ProjectRepository {
  suspend fun create(command: CreateProjectCommand): ProjectRecord

  suspend fun findByApiId(tenantId: UUID, apiId: String): ProjectRecord?
}
