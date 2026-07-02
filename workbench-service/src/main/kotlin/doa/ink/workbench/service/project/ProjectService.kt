package doa.ink.workbench.service.project

import doa.ink.workbench.core.project.ProjectRepository
import doa.ink.workbench.core.project.model.CreateProjectCommand
import doa.ink.workbench.core.project.model.ProjectRecord
import org.springframework.stereotype.Service

@Service
class ProjectService(private val repository: ProjectRepository) {
  suspend fun create(command: CreateProjectCommand): ProjectRecord = repository.create(command)
}
