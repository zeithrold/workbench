package ink.doa.workbench.web.invitation

import ink.doa.workbench.core.common.summary.TenantSummary
import ink.doa.workbench.core.common.summary.UserSummary
import ink.doa.workbench.security.invitation.InvitationAcceptView
import ink.doa.workbench.security.invitation.InvitationPreviewView
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Invitation preview for a token.")
data class InvitationPreviewResponse(
  @field:Schema(example = "TENANT_ADMIN") val type: String,
  val tenant: TenantSummary,
  val email: String,
  val displayName: String?,
) {
  companion object {
    fun from(view: InvitationPreviewView): InvitationPreviewResponse =
      InvitationPreviewResponse(
        type = view.type.name,
        tenant = view.tenant,
        email = view.email,
        displayName = view.displayName,
      )
  }
}

@Schema(description = "Successful invitation acceptance.")
data class InvitationAcceptResponse(
  @field:Schema(example = "TENANT_ADMIN") val type: String,
  val tenant: TenantSummary,
  val user: UserSummary,
) {
  companion object {
    fun from(view: InvitationAcceptView): InvitationAcceptResponse =
      InvitationAcceptResponse(
        type = view.type.name,
        tenant = view.tenant,
        user = view.user,
      )
  }
}
