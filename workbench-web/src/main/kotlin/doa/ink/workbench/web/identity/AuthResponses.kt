package doa.ink.workbench.web.identity

import doa.ink.workbench.core.common.summary.LoginMethodSummary
import doa.ink.workbench.core.common.summary.TenantSummary
import doa.ink.workbench.core.common.summary.UserSummary
import doa.ink.workbench.security.identity.FederatedAuthorizeView
import doa.ink.workbench.security.identity.IssuedTokenView
import doa.ink.workbench.security.identity.LoginDiscoveryView
import doa.ink.workbench.security.identity.LoginFlow
import doa.ink.workbench.security.identity.LoginMethodChoiceView
import doa.ink.workbench.security.identity.LoginOptionView
import doa.ink.workbench.security.identity.LoginView
import doa.ink.workbench.security.identity.TenantMembershipView
import doa.ink.workbench.web.api.OpenApiExamples
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Schema(description = "Successful login payload. Also sets the WORKBENCH_SESSION cookie.")
data class LoginResponse(
  @field:Schema(description = "Authenticated user.") val user: UserSummary,
  @field:Schema(description = "Session expiry.", example = "2026-07-02T12:00:00+00:00")
  val sessionExpiresAt: OffsetDateTime,
  @field:Schema(description = "Optional long-lived bearer token when requested at login.")
  val bearerToken: IssuedTokenResponse?,
  @field:Schema(description = "Login context after authentication.", example = "TENANT")
  val loginContext: String? = null,
  @field:Schema(description = "Active tenant when auto-bound at login.")
  val activeTenant: TenantSummary? = null,
  @field:Schema(description = "Tenants the user may select when multiple are eligible.")
  val eligibleTenants: List<TenantSummary> = emptyList(),
) {
  companion object {
    fun from(view: LoginView): LoginResponse =
      LoginResponse(
        user = view.user,
        sessionExpiresAt = view.sessionExpiresAt,
        bearerToken =
          view.bearerToken?.let {
            IssuedTokenResponse(
              id = it.id,
              token = it.token,
              expiresAt = it.expiresAt,
            )
          },
        loginContext = view.loginContext.name,
        activeTenant = view.activeTenant,
        eligibleTenants = view.eligibleTenants,
      )
  }
}

@Schema(description = "Issued bearer token credential.")
data class IssuedTokenResponse(
  @field:Schema(description = "Public token id.", example = OpenApiExamples.BEARER_TOKEN_ID)
  val id: String,
  @field:Schema(description = "Opaque bearer secret. Shown only once at issuance.")
  val token: String,
  @field:Schema(description = "Token expiry.", example = "2027-07-02T12:00:00+00:00")
  val expiresAt: OffsetDateTime,
) {
  companion object {
    fun from(view: IssuedTokenView): IssuedTokenResponse =
      IssuedTokenResponse(id = view.id, token = view.token, expiresAt = view.expiresAt)
  }
}

@Schema(description = "User membership in a tenant.")
data class MembershipResponse(
  @field:Schema(description = "Public membership id.", example = OpenApiExamples.MEMBERSHIP_ID)
  val id: String,
  @field:Schema(description = "Tenant the user belongs to.") val tenant: TenantSummary,
  @field:Schema(description = "Whether the user is a tenant administrator.")
  val isTenantAdmin: Boolean = false,
) {
  companion object {
    fun from(view: TenantMembershipView): MembershipResponse =
      MembershipResponse(
        id = view.id,
        tenant = view.tenant,
        isTenantAdmin = view.isTenantAdmin,
      )
  }
}

@Schema(description = "Wizard-oriented login discovery for an identifier.")
data class LoginDiscoveryResponse(
  val identifierRecognized: Boolean,
  val flow: LoginFlow,
  val instancePasswordMethod: LoginMethodSummary?,
  val tenantMethods: List<LoginMethodChoiceResponse>,
) {
  companion object {
    fun from(view: LoginDiscoveryView): LoginDiscoveryResponse =
      LoginDiscoveryResponse(
        identifierRecognized = view.identifierRecognized,
        flow = view.flow,
        instancePasswordMethod = view.instancePasswordMethod,
        tenantMethods = view.tenantMethods.map { LoginMethodChoiceResponse.from(it) },
      )
  }
}

data class LoginMethodChoiceResponse(
  val loginMethod: LoginMethodSummary,
  val supportedTenants: List<TenantSummary>,
) {
  companion object {
    fun from(view: LoginMethodChoiceView): LoginMethodChoiceResponse =
      LoginMethodChoiceResponse(
        loginMethod = view.loginMethod,
        supportedTenants = view.supportedTenants,
      )
  }
}

@Schema(description = "Available tenant and login method pair for sign-in discovery.")
data class LoginOptionResponse(
  @field:Schema(description = "Tenant offering this login method.") val tenant: TenantSummary,
  @field:Schema(description = "Login method available for the tenant.")
  val loginMethod: LoginMethodSummary,
) {
  companion object {
    fun from(view: LoginOptionView): LoginOptionResponse =
      LoginOptionResponse(tenant = view.tenant, loginMethod = view.loginMethod)
  }
}

@Schema(description = "Federated authorization redirect details.")
data class FederatedAuthorizeResponse(
  @field:Schema(description = "Provider authorization URL to redirect the browser to.")
  val authorizationUrl: String,
  @field:Schema(description = "Opaque state value echoed on callback.") val state: String,
) {
  companion object {
    fun from(view: FederatedAuthorizeView): FederatedAuthorizeResponse =
      FederatedAuthorizeResponse(
        authorizationUrl = view.authorizationUrl,
        state = view.state,
      )
  }
}
