package ink.doa.workbench.core.project.model

import java.util.UUID

data class UpdateProjectCommand(
  val tenantId: UUID,
  val projectId: UUID,
  val identifier: String?,
  val name: String?,
  val description: String?,
  val nonMemberVisibility: NonMemberVisibility?,
  val nonMemberJoinPolicy: NonMemberJoinPolicy?,
  val updatedBy: UUID?,
)
