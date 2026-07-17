package one.ztd.workbench.identity.auth

import one.ztd.workbench.identity.LoginAccountStore
import one.ztd.workbench.identity.LoginMethodRepository
import one.ztd.workbench.identity.TenantLoginMethodSettingRepository
import one.ztd.workbench.identity.UserLoginAccountRepository
import one.ztd.workbench.tenant.TenantRepository
import org.springframework.stereotype.Component

@Component
class MagicLinkAuthRepositories(
  val loginMethods: LoginMethodRepository,
  val tenantLoginSettings: TenantLoginMethodSettingRepository,
  val loginAccounts: LoginAccountStore,
  val userLoginAccounts: UserLoginAccountRepository,
  val tenants: TenantRepository,
  val magicLinkTokens: MagicLinkTokenRepository,
)
