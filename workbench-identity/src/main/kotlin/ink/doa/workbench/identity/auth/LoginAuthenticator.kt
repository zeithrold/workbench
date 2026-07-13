package ink.doa.workbench.identity.auth

import ink.doa.workbench.identity.model.AuthenticatedIdentity
import ink.doa.workbench.identity.model.LoginCommand
import ink.doa.workbench.identity.model.LoginMethodKind

interface LoginAuthenticator {
  val kind: LoginMethodKind

  suspend fun authenticate(command: LoginCommand): AuthenticatedIdentity
}
