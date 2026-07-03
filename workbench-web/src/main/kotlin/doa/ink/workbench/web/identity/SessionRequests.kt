package doa.ink.workbench.web.identity

import doa.ink.workbench.web.api.OpenApiExamples
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Switch the active tenant for the current session.")
data class SwitchTenantRequest(
  @field:Schema(
    description = "Public tenant id to activate. Must be a tenant the user belongs to.",
    example = OpenApiExamples.TENANT_ID_OTHER,
  )
  val tenantId: String?
)

@Schema(description = "Current session aggregate keyed by the session cookie.")
data class SessionResponse(
  @field:Schema(description = "Authenticated user summary.")
  val user: doa.ink.workbench.core.common.summary.UserSummary,
  @field:Schema(description = "Tenant currently selected in the session, if any.")
  val activeTenant: doa.ink.workbench.core.common.summary.TenantSummary?,
  @field:Schema(description = "When the session expires.", example = "2026-07-02T12:00:00+00:00")
  val sessionExpiresAt: java.time.OffsetDateTime,
  @field:Schema(description = "Administrator scopes available to the user.")
  val adminScopes: List<String> = emptyList(),
) {
  companion object {
    fun from(view: doa.ink.workbench.security.identity.SessionView): SessionResponse =
      SessionResponse(
        user = view.user,
        activeTenant = view.activeTenant,
        sessionExpiresAt = view.sessionExpiresAt,
        adminScopes = view.adminScopes,
      )
  }
}
