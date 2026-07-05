package ink.doa.workbench.security.identity.auth

import ink.doa.workbench.core.identity.AuthEventRepository
import ink.doa.workbench.core.identity.LoginAccountStore
import ink.doa.workbench.core.identity.UserRepository
import org.springframework.stereotype.Component

@Component
class AuthCredentialSupport(
  val users: UserRepository,
  val loginAccounts: LoginAccountStore,
  val authEvents: AuthEventRepository,
)
