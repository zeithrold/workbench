package one.ztd.workbench.identity.auth

import one.ztd.workbench.identity.model.AuthenticatedIdentity
import one.ztd.workbench.identity.model.LoginCommand
import one.ztd.workbench.identity.model.LoginMethodKind

interface LoginAuthenticator {
  val kind: LoginMethodKind

  suspend fun authenticate(command: LoginCommand): AuthenticatedIdentity
}
