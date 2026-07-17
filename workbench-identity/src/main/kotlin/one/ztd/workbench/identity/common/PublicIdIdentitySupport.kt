package one.ztd.workbench.identity.common

import one.ztd.workbench.identity.LoginMethodRepository
import one.ztd.workbench.identity.UserRepository
import one.ztd.workbench.identity.auth.BearerTokenRepository
import one.ztd.workbench.tenant.TenantRepository
import org.springframework.stereotype.Component

@Component
class PublicIdIdentitySupport(
  val tenants: TenantRepository,
  val users: UserRepository,
  val loginMethods: LoginMethodRepository,
  val bearerTokens: BearerTokenRepository,
)
