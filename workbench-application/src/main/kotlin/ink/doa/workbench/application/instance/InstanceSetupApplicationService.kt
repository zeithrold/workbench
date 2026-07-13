package ink.doa.workbench.application.instance

import ink.doa.workbench.application.identity.InstanceBootstrapService
import ink.doa.workbench.identity.AuthApplicationService
import ink.doa.workbench.identity.LoginView
import ink.doa.workbench.identity.common.summary.LoginMethodSummary
import ink.doa.workbench.identity.common.summary.UserSummary
import ink.doa.workbench.identity.model.BootstrapInstanceAdminCommand
import ink.doa.workbench.identity.model.LoginCommand
import ink.doa.workbench.identity.model.LoginMethodKind
import ink.doa.workbench.kernel.common.errors.SetupTokenInvalidException
import ink.doa.workbench.tenant.instance.InstanceProperties
import org.springframework.stereotype.Service

@Service
class InstanceSetupApplicationService(
  private val instanceBootstrapService: InstanceBootstrapService,
  private val instanceProperties: InstanceProperties,
  private val authApplicationService: AuthApplicationService,
) {
  suspend fun setupStatus(): InstanceSetupStatusView =
    InstanceSetupStatusView(initialized = instanceBootstrapService.isInitialized())

  suspend fun bootstrap(command: BootstrapInstanceAdminCommand): InstanceBootstrapView {
    validateSetupToken(command.setupToken)
    val bootstrap = instanceBootstrapService.bootstrap(command)
    val loginView =
      authApplicationService.login(
        LoginCommand(
          method = LoginMethodKind.PASSWORD,
          loginMethodId = bootstrap.loginMethod.apiId.value,
          subject = command.email.trim(),
          password = command.password,
          ipAddress = command.ipAddress,
          userAgent = command.userAgent,
        )
      )

    return InstanceBootstrapView(
      user = UserSummary.from(bootstrap.user),
      loginMethod = LoginMethodSummary.from(bootstrap.loginMethod),
      session = loginView,
    )
  }

  private fun validateSetupToken(provided: String?) {
    val configured = instanceProperties.setupToken
    if (configured.isNullOrBlank()) {
      return
    }
    if (provided.isNullOrBlank() || provided != configured) {
      throw SetupTokenInvalidException()
    }
  }
}

data class InstanceSetupStatusView(val initialized: Boolean)

data class InstanceBootstrapView(
  val user: UserSummary,
  val loginMethod: LoginMethodSummary,
  val session: LoginView,
)
