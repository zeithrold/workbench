package doa.ink.workbench.service.identity.auth

import doa.ink.workbench.core.identity.model.AuthenticatedIdentity
import doa.ink.workbench.core.identity.model.LoginCommand
import doa.ink.workbench.core.identity.model.LoginMethodKind

interface LoginAuthenticator {
  val kind: LoginMethodKind

  suspend fun authenticate(command: LoginCommand): AuthenticatedIdentity
}
