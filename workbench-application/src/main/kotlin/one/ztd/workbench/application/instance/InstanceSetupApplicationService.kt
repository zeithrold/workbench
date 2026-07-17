package one.ztd.workbench.application.instance

import one.ztd.workbench.application.identity.InstanceBootstrapService
import one.ztd.workbench.identity.AuthApplicationService
import one.ztd.workbench.identity.LoginView
import one.ztd.workbench.identity.common.summary.LoginMethodSummary
import one.ztd.workbench.identity.common.summary.UserSummary
import one.ztd.workbench.identity.model.BootstrapInstanceAdminCommand
import one.ztd.workbench.identity.model.LoginCommand
import one.ztd.workbench.identity.model.LoginMethodKind
import one.ztd.workbench.kernel.common.errors.SetupTokenInvalidException
import one.ztd.workbench.tenant.instance.InstanceProperties
import org.springframework.stereotype.Service

@Service
class InstanceSetupApplicationService(
  private val instanceBootstrapService: InstanceBootstrapService,
  private val instanceProperties: InstanceProperties,
  private val authApplicationService: AuthApplicationService,
) {
  suspend fun setupStatus(): InstanceSetupStatusView =
    InstanceSetupStatusView(
      initialized = instanceBootstrapService.isInitialized(),
      setupTokenRequired = !instanceProperties.setupToken.isNullOrBlank(),
    )

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

data class InstanceSetupStatusView(
  val initialized: Boolean,
  val setupTokenRequired: Boolean,
)

data class InstanceBootstrapView(
  val user: UserSummary,
  val loginMethod: LoginMethodSummary,
  val session: LoginView,
)
