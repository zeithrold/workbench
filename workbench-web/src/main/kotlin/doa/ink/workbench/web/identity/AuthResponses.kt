package doa.ink.workbench.web.identity

import doa.ink.workbench.core.common.summary.LoginMethodSummary
import doa.ink.workbench.core.common.summary.TenantSummary
import doa.ink.workbench.core.common.summary.UserSummary
import doa.ink.workbench.service.identity.FederatedAuthorizeView
import doa.ink.workbench.service.identity.IssuedTokenView
import doa.ink.workbench.service.identity.LoginOptionView
import doa.ink.workbench.service.identity.LoginView
import doa.ink.workbench.service.identity.TenantMembershipView
import doa.ink.workbench.web.api.OpenApiExamples
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Schema(description = "Successful login payload. Also sets the WORKBENCH_SESSION cookie.")
data class LoginResponse(
  @field:Schema(description = "Authenticated user.")
  val user: UserSummary,
  @field:Schema(description = "Session expiry.", example = "2026-07-02T12:00:00+00:00")
  val sessionExpiresAt: OffsetDateTime,
  @field:Schema(description = "Optional long-lived bearer token when requested at login.")
  val bearerToken: IssuedTokenResponse?,
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
  @field:Schema(description = "Tenant the user belongs to.")
  val tenant: TenantSummary,
) {
  companion object {
    fun from(view: TenantMembershipView): MembershipResponse =
      MembershipResponse(id = view.id, tenant = view.tenant)
  }
}

@Schema(description = "Available tenant and login method pair for sign-in discovery.")
data class LoginOptionResponse(
  @field:Schema(description = "Tenant offering this login method.")
  val tenant: TenantSummary,
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
  @field:Schema(description = "Opaque state value echoed on callback.")
  val state: String,
) {
  companion object {
    fun from(view: FederatedAuthorizeView): FederatedAuthorizeResponse =
      FederatedAuthorizeResponse(
        authorizationUrl = view.authorizationUrl,
        state = view.state,
      )
  }
}
