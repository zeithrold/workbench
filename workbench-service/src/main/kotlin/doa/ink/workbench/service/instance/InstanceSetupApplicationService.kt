package doa.ink.workbench.service.instance

import doa.ink.workbench.core.common.errors.SetupTokenInvalidException
import doa.ink.workbench.core.common.summary.LoginMethodSummary
import doa.ink.workbench.core.common.summary.UserSummary
import doa.ink.workbench.core.identity.model.BootstrapInstanceAdminCommand
import doa.ink.workbench.core.identity.model.LoginCommand
import doa.ink.workbench.core.identity.model.LoginMethodKind
import doa.ink.workbench.security.identity.AuthApplicationService
import doa.ink.workbench.security.identity.InstanceBootstrapService
import doa.ink.workbench.security.identity.LoginView
import doa.ink.workbench.tenant.instance.InstanceProperties
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
