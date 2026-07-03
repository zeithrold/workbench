package doa.ink.workbench.security.identity.auth

import doa.ink.workbench.core.common.errors.InvalidRequestException
import doa.ink.workbench.core.common.errors.WorkbenchErrorCode
import doa.ink.workbench.core.identity.model.AuthenticatedIdentity
import doa.ink.workbench.core.identity.model.LoginCommand
import doa.ink.workbench.core.identity.model.LoginMethodKind
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
