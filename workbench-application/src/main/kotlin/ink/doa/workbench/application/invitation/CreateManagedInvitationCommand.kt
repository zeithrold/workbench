package ink.doa.workbench.application.invitation

import ink.doa.workbench.identity.model.InvitationType
import ink.doa.workbench.kernel.common.context.RequestHost
import java.util.UUID

data class CreateManagedInvitationCommand(
  val type: InvitationType,
  val tenantId: UUID,
  val email: String,
  val displayName: String?,
  val invitedBy: UUID,
  val requestHost: RequestHost?,
)
