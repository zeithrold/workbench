package ink.doa.workbench.core.project.events

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.project.model.ProjectRecord
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
