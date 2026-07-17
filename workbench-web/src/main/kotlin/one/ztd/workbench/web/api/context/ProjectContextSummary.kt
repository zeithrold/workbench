package one.ztd.workbench.web.api.context

import java.util.UUID
import one.ztd.workbench.agile.project.model.ProjectRecord
import one.ztd.workbench.kernel.common.ids.PublicId

data class ProjectContextSummary(
  val id: UUID,
  val publicId: PublicId,
  val identifier: String,
  val name: String,
) {
  companion object {
    fun from(record: ProjectRecord): ProjectContextSummary =
      ProjectContextSummary(
        id = record.id,
        publicId = record.apiId,
        identifier = record.identifier,
        name = record.name,
      )
  }
}
