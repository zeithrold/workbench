package one.ztd.workbench.identity.auth

import one.ztd.workbench.identity.model.AuthenticatedIdentity
import one.ztd.workbench.identity.model.LoginCommand
import one.ztd.workbench.identity.model.LoginMethodKind
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import org.springframework.stereotype.Service

@Service
class LoginOrchestrator(authenticators: List<LoginAuthenticator>) {
  private val authenticatorsByKind = authenticators.associateBy { it.kind }

  suspend fun authenticate(command: LoginCommand): AuthenticatedIdentity {
    if (command.method in FEDERATED_OR_ASYNC_KINDS) {
      throw InvalidRequestException(
        WorkbenchErrorCode.IDENTITY_LOGIN_METHOD_UNSUPPORTED,
        "Login method ${command.method} must use the dedicated /api/auth endpoint.",
      )
    }
    val authenticator =
      authenticatorsByKind[command.method]
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.IDENTITY_LOGIN_METHOD_UNSUPPORTED,
          "Unsupported login method: ${command.method}",
        )
    return authenticator.authenticate(command)
  }

  companion object {
    private val FEDERATED_OR_ASYNC_KINDS =
      setOf(
        LoginMethodKind.EMAIL_MAGIC_LINK,
        LoginMethodKind.OAUTH2,
        LoginMethodKind.OIDC,
        LoginMethodKind.SAML,
      )
  }
}
