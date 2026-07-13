package ink.doa.workbench.web.api.context

import ink.doa.workbench.agile.project.model.ProjectRecord
import ink.doa.workbench.kernel.common.ids.PublicId
import java.util.UUID

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
