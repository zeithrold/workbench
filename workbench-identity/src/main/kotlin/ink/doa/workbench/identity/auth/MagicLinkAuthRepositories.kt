package ink.doa.workbench.identity.auth

import ink.doa.workbench.identity.LoginAccountStore
import ink.doa.workbench.identity.LoginMethodRepository
import ink.doa.workbench.identity.TenantLoginMethodSettingRepository
import ink.doa.workbench.identity.UserLoginAccountRepository
import ink.doa.workbench.tenant.TenantRepository
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
