package ink.doa.workbench.core.common.context

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.project.model.ProjectRecord
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
