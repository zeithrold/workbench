package ink.doa.workbench.identity.auth

import ink.doa.workbench.identity.AuthEventRepository
import ink.doa.workbench.identity.LoginAccountStore
import ink.doa.workbench.identity.UserRepository
import org.springframework.stereotype.Component

@Component
class AuthCredentialSupport(
  val users: UserRepository,
  val loginAccounts: LoginAccountStore,
  val authEvents: AuthEventRepository,
)
