package one.ztd.workbench.identity

import one.ztd.workbench.identity.auth.PasswordHasher
import one.ztd.workbench.identity.permission.AccessGrantRepository
import one.ztd.workbench.identity.permission.AdminUserCommandRepository
import one.ztd.workbench.identity.permission.AdminUserQueryRepository
import org.springframework.stereotype.Component

@Component
class BootstrapAccountSupport(
  val users: UserRepository,
  val loginMethods: LoginMethodRepository,
  val loginAccounts: LoginAccountStore,
  val userLoginAccounts: UserLoginAccountRepository,
  val passwordHasher: PasswordHasher,
)

@Component
class BootstrapAdminSupport(
  val adminUserCommands: AdminUserCommandRepository,
  val adminUserQueries: AdminUserQueryRepository,
  val accessGrants: AccessGrantRepository,
)
