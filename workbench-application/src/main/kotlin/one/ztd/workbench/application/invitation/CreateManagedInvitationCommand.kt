package one.ztd.workbench.application.invitation

import java.util.UUID
import one.ztd.workbench.identity.model.InvitationType
import one.ztd.workbench.kernel.common.context.RequestHost

data class CreateManagedInvitationCommand(
  val type: InvitationType,
  val tenantId: UUID,
  val email: String,
  val displayName: String?,
  val invitedBy: UUID,
  val requestHost: RequestHost?,
)
