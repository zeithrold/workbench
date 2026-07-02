package doa.ink.workbench.web.identity

import doa.ink.workbench.core.common.summary.LoginMethodSummary
import doa.ink.workbench.core.common.summary.TenantSummary
import doa.ink.workbench.core.common.summary.UserSummary
import doa.ink.workbench.service.identity.FederatedAuthorizeView
import doa.ink.workbench.service.identity.IssuedTokenView
import doa.ink.workbench.service.identity.LoginOptionView
import doa.ink.workbench.service.identity.LoginView
import doa.ink.workbench.service.identity.TenantMembershipView
import java.time.OffsetDateTime

data class LoginResponse(
  val user: UserSummary,
  val sessionExpiresAt: OffsetDateTime,
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

data class IssuedTokenResponse(
  val id: String,
  val token: String,
  val expiresAt: OffsetDateTime,
) {
  companion object {
    fun from(view: IssuedTokenView): IssuedTokenResponse =
      IssuedTokenResponse(id = view.id, token = view.token, expiresAt = view.expiresAt)
  }
}

data class MembershipResponse(
  val id: String,
  val tenant: TenantSummary,
) {
  companion object {
    fun from(view: TenantMembershipView): MembershipResponse =
      MembershipResponse(id = view.id, tenant = view.tenant)
  }
}

data class LoginOptionResponse(
  val tenant: TenantSummary,
  val loginMethod: LoginMethodSummary,
) {
  companion object {
    fun from(view: LoginOptionView): LoginOptionResponse =
      LoginOptionResponse(tenant = view.tenant, loginMethod = view.loginMethod)
  }
}

data class FederatedAuthorizeResponse(
  val authorizationUrl: String,
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
