package doa.ink.workbench.project

import doa.ink.workbench.project.model.CreateProjectCommand
import doa.ink.workbench.project.model.ProjectRecord
import java.util.UUID

interface ProjectRepository {
  suspend fun create(command: CreateProjectCommand): ProjectRecord

  suspend fun findByApiId(tenantId: UUID, apiId: String): ProjectRecord?
}
