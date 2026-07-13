package ink.doa.workbench.identity.common

import ink.doa.workbench.identity.LoginMethodRepository
import ink.doa.workbench.identity.UserRepository
import ink.doa.workbench.identity.auth.BearerTokenRepository
import ink.doa.workbench.tenant.TenantRepository
import org.springframework.stereotype.Component

@Component
class PublicIdIdentitySupport(
  val tenants: TenantRepository,
  val users: UserRepository,
  val loginMethods: LoginMethodRepository,
  val bearerTokens: BearerTokenRepository,
)
