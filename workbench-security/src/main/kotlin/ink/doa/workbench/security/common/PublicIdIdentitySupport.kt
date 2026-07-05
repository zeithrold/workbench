package ink.doa.workbench.security.common

import ink.doa.workbench.core.identity.LoginMethodRepository
import ink.doa.workbench.core.identity.TenantRepository
import ink.doa.workbench.core.identity.UserRepository
import ink.doa.workbench.core.identity.auth.BearerTokenRepository
import org.springframework.stereotype.Component

@Component
class PublicIdIdentitySupport(
  val tenants: TenantRepository,
  val users: UserRepository,
  val loginMethods: LoginMethodRepository,
  val bearerTokens: BearerTokenRepository,
)
