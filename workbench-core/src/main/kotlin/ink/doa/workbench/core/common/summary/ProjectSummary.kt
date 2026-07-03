package ink.doa.workbench.core.common.summary

import ink.doa.workbench.core.project.model.ProjectRecord

data class ProjectSummary(
  val id: String,
  val identifier: String,
  val name: String,
) {
  companion object {
    fun from(record: ProjectRecord): ProjectSummary =
      ProjectSummary(
        id = record.apiId.value,
        identifier = record.identifier,
        name = record.name,
      )
  }
}
