package doa.ink.workbench.core.identity.auth

import doa.ink.workbench.core.identity.model.AuthenticatedPrincipal

interface SessionAuthenticator {
  suspend fun authenticate(sessionId: String): AuthenticatedPrincipal?
}

interface BearerTokenAuthenticator {
  suspend fun authenticate(token: String): AuthenticatedPrincipal?
}
