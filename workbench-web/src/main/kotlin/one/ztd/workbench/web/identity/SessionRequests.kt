package one.ztd.workbench.web.identity

import io.swagger.v3.oas.annotations.media.Schema
import one.ztd.workbench.web.api.OpenApiExamples

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
  val user: one.ztd.workbench.identity.common.summary.UserSummary,
  @field:Schema(description = "Tenant currently selected in the session, if any.")
  val activeTenant: one.ztd.workbench.tenant.common.summary.TenantSummary?,
  @field:Schema(description = "When the session expires.", example = "2026-07-02T12:00:00+00:00")
  val sessionExpiresAt: java.time.OffsetDateTime,
  @field:Schema(description = "Administrator scopes available to the user.")
  val adminScopes: List<String> = emptyList(),
  @field:Schema(description = "Locale preferences used to resolve the client language.")
  val localeContext: LocaleContextResponse = LocaleContextResponse(),
) {
  companion object {
    fun from(view: one.ztd.workbench.identity.SessionView): SessionResponse =
      SessionResponse(
        user = view.user,
        activeTenant = view.activeTenant,
        sessionExpiresAt = view.sessionExpiresAt,
        adminScopes = view.adminScopes,
        localeContext = LocaleContextResponse.from(view.localeContext),
      )
  }
}

@Schema(description = "Locale preference context for the current user and active tenant.")
data class LocaleContextResponse(
  @field:Schema(
    description = "User override, or null to follow the tenant default.",
    types = ["string", "null"],
  )
  val userPreference: String? = null,
  @field:Schema(
    description = "Default locale of the active tenant, if one is selected.",
    types = ["string", "null"],
  )
  val tenantDefault: String? = null,
) {
  companion object {
    fun from(view: one.ztd.workbench.identity.LocaleContextView): LocaleContextResponse =
      LocaleContextResponse(
        userPreference = view.userPreference,
        tenantDefault = view.tenantDefault,
      )
  }
}
