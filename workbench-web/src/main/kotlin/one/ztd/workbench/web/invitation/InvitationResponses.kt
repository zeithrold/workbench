package one.ztd.workbench.web.invitation

import io.swagger.v3.oas.annotations.media.Schema
import one.ztd.workbench.application.invitation.InvitationAcceptView
import one.ztd.workbench.application.invitation.InvitationPreviewView
import one.ztd.workbench.identity.common.summary.UserSummary
import one.ztd.workbench.tenant.common.summary.TenantSummary

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
