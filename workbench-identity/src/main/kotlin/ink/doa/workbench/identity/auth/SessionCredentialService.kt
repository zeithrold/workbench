package ink.doa.workbench.identity.auth

import ink.doa.workbench.identity.model.AuditEventResult
import ink.doa.workbench.identity.model.AuthEventType
import ink.doa.workbench.identity.model.AuthenticatedPrincipal
import ink.doa.workbench.identity.model.CreateAuthEventCommand
import ink.doa.workbench.identity.model.CreateAuthSessionCommand
import ink.doa.workbench.identity.model.CredentialType
import ink.doa.workbench.identity.model.IssuedCredential
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class SessionCredentialService(
  private val credentials: AuthCredentialSupport,
  private val crypto: CredentialCryptoSupport,
  private val sessions: AuthSessionRepository,
  private val clock: Clock,
) : SessionAuthenticator {
  private val defaultSessionTtl = Duration.ofHours(12)

  suspend fun issueSession(
    userId: UUID,
    loginAccountId: UUID,
    now: OffsetDateTime,
    activeTenantId: UUID? = null,
  ): IssuedCredential {
    val secret = crypto.secretGenerator.generate()
    val sessionTtl = sessionTtlForUser(userId)
    val session =
      sessions.create(
        CreateAuthSessionCommand(
          sessionHash = crypto.credentialHasher.hash(secret),
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
      sessions.findActiveByHash(crypto.credentialHasher.hash(sessionSecret), now) ?: return false
    val revoked = sessions.revoke(session.id, now)
    if (revoked) {
      credentials.authEvents.append(
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
    val session = sessions.findActiveByHash(crypto.credentialHasher.hash(sessionId), now)
    return session?.let {
      credentials.users.findById(it.userId)?.let { user ->
        sessions.touch(it.id, now)
        credentials.loginAccounts.touchLastUsed(it.loginAccountId, now)
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
    credentials.users.findById(userId)
    return defaultSessionTtl
  }

  private fun now(): OffsetDateTime = OffsetDateTime.now(clock)
}
