package ink.doa.workbench.application.identity

import ink.doa.workbench.application.permission.InstanceAdminGrantProvisioner
import ink.doa.workbench.identity.BootstrapAccountSupport
import ink.doa.workbench.identity.BootstrapAdminSupport
import ink.doa.workbench.identity.auth.normalizeSubject
import ink.doa.workbench.identity.model.BootstrapInstanceAdminCommand
import ink.doa.workbench.identity.model.CreateLoginAccountCommand
import ink.doa.workbench.identity.model.CreateUserCommand
import ink.doa.workbench.identity.model.LinkUserLoginAccountCommand
import ink.doa.workbench.identity.model.LoginAccountParameterKey
import ink.doa.workbench.identity.model.LoginMethodDefinitionRecord
import ink.doa.workbench.identity.model.UpsertLoginAccountParameterCommand
import ink.doa.workbench.identity.model.UserRecord
import ink.doa.workbench.identity.permission.AdminScope
import ink.doa.workbench.identity.permission.CreateAdminUserCommand
import ink.doa.workbench.kernel.common.errors.InstanceAlreadyInitializedException
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import java.time.Clock
import java.time.OffsetDateTime
import org.springframework.stereotype.Service

private const val INSTANCE_PASSWORD_METHOD_CODE = "instance_password"

@Service
class InstanceBootstrapService(
  private val accounts: BootstrapAccountSupport,
  private val admin: BootstrapAdminSupport,
  private val clock: Clock,
  private val instanceAdminGrantProvisioner: InstanceAdminGrantProvisioner =
    InstanceAdminGrantProvisioner(admin.accessGrants),
) {
  suspend fun isInitialized(): Boolean = admin.adminUserQueries.existsActiveInstanceAdmin()

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
    instanceAdminGrantProvisioner.provision(user.id, user.id, now)

    return InstanceBootstrapResult(user = user, loginMethod = instancePasswordMethod)
  }
}

data class InstanceBootstrapResult(
  val user: UserRecord,
  val loginMethod: LoginMethodDefinitionRecord,
)
