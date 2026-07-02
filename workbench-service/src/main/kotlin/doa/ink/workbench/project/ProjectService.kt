package doa.ink.workbench.project

import doa.ink.workbench.project.model.CreateProjectCommand
import doa.ink.workbench.project.model.ProjectRecord
import org.springframework.stereotype.Service

@Service
class ProjectService(private val repository: ProjectRepository) {
  suspend fun create(command: CreateProjectCommand): ProjectRecord = repository.create(command)
}
