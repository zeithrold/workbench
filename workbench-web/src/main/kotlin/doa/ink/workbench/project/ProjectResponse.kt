package doa.ink.workbench.project

import doa.ink.workbench.project.model.ProjectRecord

data class ProjectResponse(
  val apiId: String,
  val identifier: String,
  val name: String,
  val description: String?,
) {
  companion object {
    fun from(record: ProjectRecord): ProjectResponse =
      ProjectResponse(
        apiId = record.apiId.value,
        identifier = record.identifier,
        name = record.name,
        description = record.description,
      )
  }
}
