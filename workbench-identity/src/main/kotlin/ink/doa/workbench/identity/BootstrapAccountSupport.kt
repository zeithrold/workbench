package ink.doa.workbench.identity

import ink.doa.workbench.identity.auth.PasswordHasher
import ink.doa.workbench.identity.permission.AccessGrantRepository
import ink.doa.workbench.identity.permission.AdminUserCommandRepository
import ink.doa.workbench.identity.permission.AdminUserQueryRepository
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
