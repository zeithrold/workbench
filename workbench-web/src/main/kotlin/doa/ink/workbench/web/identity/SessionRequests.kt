package doa.ink.workbench.web.identity

data class SwitchTenantRequest(val tenantId: String?)

data class SessionResponse(
  val user: doa.ink.workbench.core.common.summary.UserSummary,
  val activeTenant: doa.ink.workbench.core.common.summary.TenantSummary?,
  val sessionExpiresAt: java.time.OffsetDateTime,
) {
  companion object {
    fun from(view: doa.ink.workbench.service.identity.SessionView): SessionResponse =
      SessionResponse(
        user = view.user,
        activeTenant = view.activeTenant,
        sessionExpiresAt = view.sessionExpiresAt,
      )
  }
}
