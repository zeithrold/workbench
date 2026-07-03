package doa.ink.workbench.service.instance

import doa.ink.workbench.core.common.errors.InstanceAlreadyInitializedException
import doa.ink.workbench.core.common.errors.InvalidRequestException
import doa.ink.workbench.core.common.errors.SetupTokenInvalidException
import doa.ink.workbench.core.common.summary.LoginMethodSummary
import doa.ink.workbench.core.common.summary.UserSummary
import doa.ink.workbench.core.identity.LoginAccountStore
import doa.ink.workbench.core.identity.LoginMethodRepository
import doa.ink.workbench.core.identity.UserLoginAccountRepository
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
import doa.ink.workbench.core.permission.AccessGrantRepository
import doa.ink.workbench.core.permission.AdminScope
import doa.ink.workbench.core.permission.AdminUserCommandRepository
import doa.ink.workbench.core.permission.AdminUserQueryRepository
import doa.ink.workbench.core.permission.CreateAccessGrantCommand
import doa.ink.workbench.core.permission.CreateAdminUserCommand
import doa.ink.workbench.core.permission.GrantScope
import doa.ink.workbench.core.permission.model.AuthorizationAction
import doa.ink.workbench.service.identity.AuthApplicationService
import doa.ink.workbench.service.identity.LoginView
import doa.ink.workbench.service.identity.auth.normalizeSubject
import java.time.Clock
import java.time.OffsetDateTime
import org.springframework.stereotype.Service

private const val INSTANCE_PASSWORD_METHOD_CODE = "instance_password"

private val DEFAULT_INSTANCE_GRANTS =
  listOf(
    AuthorizationAction("tenant.create") to "tenant:*",
    AuthorizationAction("tenant.read") to "tenant:*",
    AuthorizationAction("tenant.update") to "tenant:*",
  )

@Service
class InstanceSetupService(
  private val users: UserRepository,
  private val loginMethods: LoginMethodRepository,
  private val loginAccounts: LoginAccountStore,
  private val userLoginAccounts: UserLoginAccountRepository,
  private val adminUserCommands: AdminUserCommandRepository,
  private val adminUserQueries: AdminUserQueryRepository,
  private val accessGrants: AccessGrantRepository,
  private val passwordHasher: PasswordHasher,
  private val instanceProperties: InstanceProperties,
  private val authApplicationService: AuthApplicationService,
  private val clock: Clock,
) {
  suspend fun setupStatus(): InstanceSetupStatusView =
    InstanceSetupStatusView(initialized = adminUserQueries.existsActiveInstanceAdmin())

  @Suppress("LongMethod")
  suspend fun bootstrap(command: BootstrapInstanceAdminCommand): InstanceBootstrapView {
    validateSetupToken(command.setupToken)
    if (adminUserQueries.existsActiveInstanceAdmin()) {
      throw InstanceAlreadyInitializedException("Instance is already initialized.")
    }

    val instancePasswordMethod =
      loginMethods.findLoginMethodByCode(INSTANCE_PASSWORD_METHOD_CODE)
        ?: throw InvalidRequestException("Instance password login method is not configured.")

    val normalizedEmail = normalizeSubject(command.email)
    val user =
      users.create(
        CreateUserCommand(
          displayName = command.displayName,
          primaryEmail = normalizedEmail,
        )
      )
    val loginAccount =
      loginAccounts.createLoginAccount(
        CreateLoginAccountCommand(
          loginMethodId = instancePasswordMethod.id,
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
    userLoginAccounts.linkUser(
      LinkUserLoginAccountCommand(
        userId = user.id,
        loginAccountId = loginAccount.id,
        linkedBy = user.id,
      )
    )

    val now = OffsetDateTime.now(clock)
    adminUserCommands.create(
      CreateAdminUserCommand(
        userId = user.id,
        scope = AdminScope.INSTANCE,
        grantedBy = user.id,
        validFrom = now,
      )
    )
    DEFAULT_INSTANCE_GRANTS.forEach { (action, pattern) ->
      accessGrants.create(
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

    val loginView =
      authApplicationService.login(
        LoginCommand(
          method = LoginMethodKind.PASSWORD,
          loginMethodId = instancePasswordMethod.apiId.value,
          subject = command.email.trim(),
          password = command.password,
          ipAddress = command.ipAddress,
          userAgent = command.userAgent,
        )
      )

    return InstanceBootstrapView(
      user = UserSummary.from(user),
      loginMethod = LoginMethodSummary.from(instancePasswordMethod),
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
