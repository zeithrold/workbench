package one.ztd.workbench.agile.project

import one.ztd.workbench.agile.project.model.ProjectRecord
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.kernel.common.warning.meta.ProjectWarningEmbed

/**
 * Wire-safe project embed. [id] is a typed public id (`prj_` + ULID). Prefer [from] for production
 * mapping.
 */
data class ProjectSummary(
  override val id: PublicId,
  override val identifier: String,
  override val name: String,
) : ProjectWarningEmbed {
  companion object {
    fun from(record: ProjectRecord): ProjectSummary =
      ProjectSummary(
        id = record.apiId,
        identifier = record.identifier,
        name = record.name,
      )
  }
}
