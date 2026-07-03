package doa.ink.workbench.security.identity.auth

import doa.ink.workbench.core.identity.AuthEventRepository
import doa.ink.workbench.core.identity.LoginAccountStore
import doa.ink.workbench.core.identity.UserRepository
import doa.ink.workbench.core.identity.auth.AuthSessionRepository
import doa.ink.workbench.core.identity.auth.CredentialHasher
import doa.ink.workbench.core.identity.auth.CredentialSecretGenerator
import doa.ink.workbench.core.identity.auth.SessionAuthenticator
import doa.ink.workbench.core.identity.model.AuditEventResult
import doa.ink.workbench.core.identity.model.AuthEventType
import doa.ink.workbench.core.identity.model.AuthenticatedPrincipal
import doa.ink.workbench.core.identity.model.CreateAuthEventCommand
import doa.ink.workbench.core.identity.model.CreateAuthSessionCommand
import doa.ink.workbench.core.identity.model.CredentialType
import doa.ink.workbench.core.identity.model.IssuedCredential
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class SessionCredentialService(
  private val users: UserRepository,
  private val loginAccounts: LoginAccountStore,
  private val authEvents: AuthEventRepository,
  private val sessions: AuthSessionRepository,
  private val secretGenerator: CredentialSecretGenerator,
  private val credentialHasher: CredentialHasher,
  private val clock: Clock,
) : SessionAuthenticator {
  private val defaultSessionTtl = Duration.ofHours(12)

  suspend fun issueSession(
    userId: UUID,
    loginAccountId: UUID,
    now: OffsetDateTime,
    activeTenantId: UUID? = null,
  ): IssuedCredential {
    val secret = secretGenerator.generate()
    val sessionTtl = sessionTtlForUser(userId)
    val session =
      sessions.create(
        CreateAuthSessionCommand(
          sessionHash = credentialHasher.hash(secret),
          userId = userId,
          loginAccountId = loginAccountId,
          expiresAt = now.plus(sessionTtl),
          activeTenantId = activeTenantId,
        )
      )
    return IssuedCredential(session.id, null, secret, session.expiresAt)
  }

  suspend fun logoutSession(
    sessionSecret: String,
    ipAddress: String?,
    userAgent: String?,
  ): Boolean {
    val now = now()
    val session =
      sessions.findActiveByHash(credentialHasher.hash(sessionSecret), now) ?: return false
    val revoked = sessions.revoke(session.id, now)
    if (revoked) {
      authEvents.append(
        CreateAuthEventCommand(
          userId = session.userId,
          loginAccountId = session.loginAccountId,
          eventType = AuthEventType.LOGOUT,
          result = AuditEventResult.SUCCESS,
          ipAddress = ipAddress,
          userAgent = userAgent,
        )
      )
    }
    return revoked
  }

  override suspend fun authenticateSession(sessionId: String): AuthenticatedPrincipal? {
    val now = now()
    val session = sessions.findActiveByHash(credentialHasher.hash(sessionId), now)
    return session?.let {
      users.findById(it.userId)?.let { user ->
        sessions.touch(it.id, now)
        loginAccounts.touchLastUsed(it.loginAccountId, now)
        AuthenticatedPrincipal(
          user = user,
          loginAccountId = it.loginAccountId,
          sessionId = it.id.toString(),
          bearerTokenId = null,
          credentialType = CredentialType.SESSION,
          tenantId = it.activeTenantId,
        )
      }
    }
  }

  private suspend fun sessionTtlForUser(userId: UUID): Duration {
    users.findById(userId)
    return defaultSessionTtl
  }

  private fun now(): OffsetDateTime = OffsetDateTime.now(clock)
}
