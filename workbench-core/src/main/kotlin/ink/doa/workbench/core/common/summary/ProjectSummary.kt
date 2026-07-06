package ink.doa.workbench.core.common.summary

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.project.model.ProjectRecord

/**
 * Wire-safe project embed. [id] is a typed public id (`prj_` + ULID). Prefer [from] for production
 * mapping.
 */
data class ProjectSummary(
  val id: PublicId,
  val identifier: String,
  val name: String,
) {
  companion object {
    fun from(record: ProjectRecord): ProjectSummary =
      ProjectSummary(
        id = record.apiId,
        identifier = record.identifier,
        name = record.name,
      )
  }
}
