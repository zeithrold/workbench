package one.ztd.workbench.agile.project

import java.util.UUID

data class ProjectMemberMutationCommand(
  val tenantId: UUID,
  val projectId: UUID,
  val userPublicId: String,
  val policyPublicId: String? = null,
  val role: String? = null,
  val actorUserId: UUID? = null,
)
