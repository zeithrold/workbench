package ink.doa.workbench.security.identity.auth

import ink.doa.workbench.core.identity.LoginAccountStore
import ink.doa.workbench.core.identity.LoginMethodRepository
import ink.doa.workbench.core.identity.TenantLoginMethodSettingRepository
import ink.doa.workbench.core.identity.TenantRepository
import ink.doa.workbench.core.identity.UserLoginAccountRepository
import ink.doa.workbench.core.identity.auth.MagicLinkTokenRepository
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
