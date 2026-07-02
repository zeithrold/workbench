package doa.ink.workbench.core.common.summary

import doa.ink.workbench.core.project.model.ProjectRecord

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
