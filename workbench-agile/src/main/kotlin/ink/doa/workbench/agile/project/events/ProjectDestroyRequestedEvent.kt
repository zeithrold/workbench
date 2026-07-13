package ink.doa.workbench.agile.project.events

import ink.doa.workbench.agile.project.model.ProjectRecord
import ink.doa.workbench.kernel.common.ids.PublicId
import java.time.OffsetDateTime
import kotlinx.serialization.Serializable

@Serializable
data class ProjectDestroyRequestedEvent(
  val tenantId: String,
  val projectId: String,
  val requestedBy: String,
  val deleteReason: String?,
  val requestedAt: String,
) {
  companion object {
    fun from(
      project: ProjectRecord,
      tenantPublicId: PublicId,
      deleteReason: String?,
      requestedAt: OffsetDateTime,
      requestedByPublicId: PublicId,
    ): ProjectDestroyRequestedEvent =
      ProjectDestroyRequestedEvent(
        tenantId = tenantPublicId.value,
        projectId = project.apiId.value,
        requestedBy = requestedByPublicId.value,
        deleteReason = deleteReason,
        requestedAt = requestedAt.toString(),
      )
  }
}
