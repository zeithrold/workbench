package ink.doa.workbench.security.identity

import ink.doa.workbench.core.common.errors.InstanceAlreadyInitializedException
import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.identity.model.BootstrapInstanceAdminCommand
import ink.doa.workbench.core.identity.model.CreateLoginAccountCommand
import ink.doa.workbench.core.identity.model.CreateUserCommand
import ink.doa.workbench.core.identity.model.LinkUserLoginAccountCommand
import ink.doa.workbench.core.identity.model.LoginAccountParameterKey
import ink.doa.workbench.core.identity.model.LoginMethodDefinitionRecord
import ink.doa.workbench.core.identity.model.UpsertLoginAccountParameterCommand
import ink.doa.workbench.core.identity.model.UserRecord
import ink.doa.workbench.core.permission.AdminScope
import ink.doa.workbench.core.permission.CreateAccessGrantCommand
import ink.doa.workbench.core.permission.CreateAdminUserCommand
import ink.doa.workbench.core.permission.GrantScope
import ink.doa.workbench.core.permission.model.AuthorizationAction
import ink.doa.workbench.security.identity.auth.normalizeSubject
import java.time.Clock
import java.time.OffsetDateTime
import org.springframework.stereotype.Service

private const val INSTANCE_PASSWORD_METHOD_CODE = "instance_password"

private val DEFAULT_INSTANCE_GRANTS =
  listOf(
    AuthorizationAction("tenant.create") to "tenant:*",
    AuthorizationAction("tenant.read") to "tenant:*",
    AuthorizationAction("tenant.update") to "tenant:*",
    AuthorizationAction("tenant.delete") to "tenant:*",
  )

@Service
class InstanceBootstrapService(
  private val accounts: BootstrapAccountSupport,
  private val admin: BootstrapAdminSupport,
  private val clock: Clock,
) {
  suspend fun isInitialized(): Boolean = admin.adminUserQueries.existsActiveInstanceAdmin()

  @Suppress("LongMethod")
  suspend fun bootstrap(command: BootstrapInstanceAdminCommand): InstanceBootstrapResult {
    if (isInitialized()) {
      throw InstanceAlreadyInitializedException()
    }

    val instancePasswordMethod =
      accounts.loginMethods.findLoginMethodByCode(INSTANCE_PASSWORD_METHOD_CODE)
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.RESOURCE_INSTANCE_PASSWORD_LOGIN_METHOD_NOT_FOUND
        )

    val normalizedEmail = normalizeSubject(command.email)
    val user =
      accounts.users.create(
        CreateUserCommand(
          displayName = command.displayName,
          primaryEmail = normalizedEmail,
        )
      )
    val loginAccount =
      accounts.loginAccounts.createLoginAccount(
        CreateLoginAccountCommand(
          loginMethodId = instancePasswordMethod.id,
          subject = command.email.trim(),
          normalizedSubject = normalizedEmail,
          displayName = command.displayName,
        )
      )
    accounts.loginAccounts.upsertParameter(
      UpsertLoginAccountParameterCommand(
        loginAccountId = loginAccount.id,
        parameterKey = LoginAccountParameterKey.PasswordHash,
        parameterValue = accounts.passwordHasher.hash(command.password),
      )
    )
    accounts.userLoginAccounts.linkUser(
      LinkUserLoginAccountCommand(
        userId = user.id,
        loginAccountId = loginAccount.id,
        linkedBy = user.id,
      )
    )

    val now = OffsetDateTime.now(clock)
    admin.adminUserCommands.create(
      CreateAdminUserCommand(
        userId = user.id,
        scope = AdminScope.INSTANCE,
        grantedBy = user.id,
        validFrom = now,
      )
    )
    DEFAULT_INSTANCE_GRANTS.forEach { (action, pattern) ->
      admin.accessGrants.create(
        CreateAccessGrantCommand(
          scope = GrantScope.INSTANCE,
          subjectUserId = user.id,
          action = action,
          resourcePattern = pattern,
          validFrom = now,
          grantedBy = user.id,
        )
      )
    }

    return InstanceBootstrapResult(user = user, loginMethod = instancePasswordMethod)
  }
}

data class InstanceBootstrapResult(
  val user: UserRecord,
  val loginMethod: LoginMethodDefinitionRecord,
)
