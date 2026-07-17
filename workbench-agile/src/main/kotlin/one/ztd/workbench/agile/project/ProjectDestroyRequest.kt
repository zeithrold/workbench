package one.ztd.workbench.agile.project

import java.util.UUID
import one.ztd.workbench.agile.project.events.ProjectDestroyRequestedEvent

data class ProjectDestroyRequest(
  val tenantId: UUID,
  val projectId: UUID,
  val deletedBy: UUID,
  val deleteReason: String?,
  val projectApiId: String,
  val tenantApiId: String,
  val payload: ProjectDestroyRequestedEvent,
)
