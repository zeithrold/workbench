package one.ztd.workbench.agile.project.events

import java.time.OffsetDateTime
import kotlinx.serialization.Serializable
import one.ztd.workbench.agile.project.model.ProjectRecord

@Serializable
data class ProjectDestroyedEvent(
  val tenantId: String,
  val projectId: String,
  val deletedAt: String,
  val deleteReason: String?,
) {
  companion object {
    fun from(
      project: ProjectRecord,
      tenantPublicId: String,
      deletedAt: OffsetDateTime,
      deleteReason: String?,
    ): ProjectDestroyedEvent =
      ProjectDestroyedEvent(
        tenantId = tenantPublicId,
        projectId = project.apiId.value,
        deletedAt = deletedAt.toString(),
        deleteReason = deleteReason,
      )
  }
}
