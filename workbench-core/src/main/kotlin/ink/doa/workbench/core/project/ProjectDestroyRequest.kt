package ink.doa.workbench.core.project

import ink.doa.workbench.core.project.events.ProjectDestroyRequestedEvent
import java.util.UUID

data class ProjectDestroyRequest(
  val tenantId: UUID,
  val projectId: UUID,
  val deletedBy: UUID,
  val deleteReason: String?,
  val projectApiId: String,
  val tenantApiId: String,
  val payload: ProjectDestroyRequestedEvent,
)
