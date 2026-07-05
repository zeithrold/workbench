package ink.doa.workbench.security.invitation

import ink.doa.workbench.core.common.context.RequestHost
import ink.doa.workbench.core.identity.model.InvitationType
import java.util.UUID

data class CreateManagedInvitationCommand(
  val type: InvitationType,
  val tenantId: UUID,
  val email: String,
  val displayName: String?,
  val invitedBy: UUID,
  val requestHost: RequestHost?,
)
