package doa.ink.workbench.service.instance

import doa.ink.workbench.core.common.errors.InstanceAlreadyInitializedException
import doa.ink.workbench.core.common.errors.InvalidRequestException
import doa.ink.workbench.core.common.errors.SetupTokenInvalidException
import doa.ink.workbench.core.common.summary.LoginMethodSummary
import doa.ink.workbench.core.common.summary.UserSummary
import doa.ink.workbench.core.identity.LoginAccountRepository
import doa.ink.workbench.core.identity.UserRepository
import doa.ink.workbench.core.identity.auth.PasswordHasher
import doa.ink.workbench.core.identity.model.BootstrapInstanceAdminCommand
import doa.ink.workbench.core.identity.model.CreateLoginAccountCommand
import doa.ink.workbench.core.identity.model.CreateUserCommand
import doa.ink.workbench.core.identity.model.LinkUserLoginAccountCommand
import doa.ink.workbench.core.identity.model.LoginAccountParameterKey
import doa.ink.workbench.core.identity.model.LoginCommand
import doa.ink.workbench.core.identity.model.LoginMethodKind
import doa.ink.workbench.core.identity.model.UpsertLoginAccountParameterCommand
import doa.ink.workbench.service.identity.AuthApplicationService
import doa.ink.workbench.service.identity.LoginView
import doa.ink.workbench.service.identity.auth.normalizeSubject
import org.springframework.stereotype.Service

private const val PASSWORD_METHOD_CODE = "password"

@Service
class InstanceSetupService(
  private val users: UserRepository,
  private val loginAccounts: LoginAccountRepository,
  private val passwordHasher: PasswordHasher,
  private val instanceProperties: InstanceProperties,
  private val authApplicationService: AuthApplicationService,
) {
  suspend fun setupStatus(): InstanceSetupStatusView =
    InstanceSetupStatusView(initialized = users.existsSystemUser())

  suspend fun bootstrap(command: BootstrapInstanceAdminCommand): InstanceBootstrapView {
    validateSetupToken(command.setupToken)
    if (users.existsSystemUser()) {
      throw InstanceAlreadyInitializedException("Instance is already initialized.")
    }

    val passwordMethod =
      loginAccounts.findLoginMethodByCode(PASSWORD_METHOD_CODE)
        ?: throw InvalidRequestException("Password login method is not configured.")

    val normalizedEmail = normalizeSubject(command.email)
    val user =
      users.create(
        CreateUserCommand(
          displayName = command.displayName,
          primaryEmail = normalizedEmail,
          isSystem = true,
        )
      )
    val loginAccount =
      loginAccounts.createLoginAccount(
        CreateLoginAccountCommand(
          loginMethodId = passwordMethod.id,
          subject = command.email.trim(),
          normalizedSubject = normalizedEmail,
          displayName = command.displayName,
        )
      )
    loginAccounts.upsertParameter(
      UpsertLoginAccountParameterCommand(
        loginAccountId = loginAccount.id,
        parameterKey = LoginAccountParameterKey.PasswordHash,
        parameterValue = passwordHasher.hash(command.password),
      )
    )
    loginAccounts.linkUser(
      LinkUserLoginAccountCommand(
        userId = user.id,
        loginAccountId = loginAccount.id,
        linkedBy = user.id,
      )
    )

    val loginView =
      authApplicationService.login(
        LoginCommand(
          method = LoginMethodKind.PASSWORD,
          loginMethodId = passwordMethod.apiId.value,
          subject = command.email.trim(),
          password = command.password,
          ipAddress = command.ipAddress,
          userAgent = command.userAgent,
        )
      )

    return InstanceBootstrapView(
      user = UserSummary.from(user),
      loginMethod = LoginMethodSummary.from(passwordMethod),
      session = loginView,
    )
  }

  private fun validateSetupToken(provided: String?) {
    val configured = instanceProperties.setupToken
    if (configured.isNullOrBlank()) {
      return
    }
    if (provided.isNullOrBlank() || provided != configured) {
      throw SetupTokenInvalidException("Setup token is invalid.")
    }
  }
}

data class InstanceSetupStatusView(val initialized: Boolean)

data class InstanceBootstrapView(
  val user: UserSummary,
  val loginMethod: LoginMethodSummary,
  val session: LoginView,
)
