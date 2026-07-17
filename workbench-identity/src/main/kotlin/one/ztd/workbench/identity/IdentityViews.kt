package one.ztd.workbench.identity

import java.time.OffsetDateTime
import one.ztd.workbench.identity.common.summary.LoginMethodSummary
import one.ztd.workbench.identity.common.summary.UserSummary
import one.ztd.workbench.identity.model.TenantLoginOption
import one.ztd.workbench.tenant.common.summary.TenantSummary

data class LocaleContextView(
  val userPreference: String? = null,
  val tenantDefault: String? = null,
)

data class SessionView(
  val user: UserSummary,
  val activeTenant: TenantSummary?,
  val sessionExpiresAt: OffsetDateTime,
  val adminScopes: List<String> = emptyList(),
  val localeContext: LocaleContextView = LocaleContextView(),
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
  val localeContext: LocaleContextView = LocaleContextView(),
)

data class UserPreferencesView(val locale: String?)

data class FederatedAuthorizeView(
  val authorizationUrl: String,
  val state: String,
)
