package one.ztd.workbench.identity.auth

import one.ztd.workbench.identity.AuthEventRepository
import one.ztd.workbench.identity.LoginAccountStore
import one.ztd.workbench.identity.UserRepository
import org.springframework.stereotype.Component

@Component
class AuthCredentialSupport(
  val users: UserRepository,
  val loginAccounts: LoginAccountStore,
  val authEvents: AuthEventRepository,
)
