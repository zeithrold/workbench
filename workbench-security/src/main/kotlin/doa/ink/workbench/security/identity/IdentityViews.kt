package doa.ink.workbench.security.identity

import doa.ink.workbench.core.common.summary.LoginMethodSummary
import doa.ink.workbench.core.common.summary.TenantSummary
import doa.ink.workbench.core.common.summary.UserSummary
import doa.ink.workbench.core.identity.model.TenantLoginOption
import java.time.OffsetDateTime

data class SessionView(
  val user: UserSummary,
  val activeTenant: TenantSummary?,
  val sessionExpiresAt: OffsetDateTime,
  val adminScopes: List<String> = emptyList(),
)

data class TenantMembershipView(
  val id: String,
  val tenant: TenantSummary,
  val isTenantAdmin: Boolean = false,
)

data class LoginOptionView(
  val tenant: TenantSummary,
  val loginMethod: LoginMethodSummary,
) {
  companion object {
    fun from(option: TenantLoginOption): LoginOptionView =
      LoginOptionView(tenant = option.tenant, loginMethod = option.loginMethod)
  }
}

data class IssuedTokenView(
  val id: String,
  val token: String,
  val expiresAt: OffsetDateTime,
)

data class LoginView(
  val user: UserSummary,
  val sessionExpiresAt: OffsetDateTime,
  val sessionSecret: String,
  val bearerToken: IssuedTokenView?,
  val loginContext: LoginContext = LoginContext.TENANT,
  val activeTenant: TenantSummary? = null,
  val eligibleTenants: List<TenantSummary> = emptyList(),
)

data class FederatedAuthorizeView(
  val authorizationUrl: String,
  val state: String,
)
