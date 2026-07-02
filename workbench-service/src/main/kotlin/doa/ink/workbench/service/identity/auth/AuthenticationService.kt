package doa.ink.workbench.service.identity.auth

import doa.ink.workbench.core.common.errors.AuthenticationFailedException
import doa.ink.workbench.core.common.errors.InvalidRequestException
import doa.ink.workbench.core.identity.AuthEventRepository
import doa.ink.workbench.core.identity.LoginAccountRepository
import doa.ink.workbench.core.identity.UserRepository
import doa.ink.workbench.core.identity.auth.AuthSessionRepository
import doa.ink.workbench.core.identity.auth.BearerTokenAuthenticator
import doa.ink.workbench.core.identity.auth.BearerTokenRepository
import doa.ink.workbench.core.identity.auth.CredentialHasher
import doa.ink.workbench.core.identity.auth.CredentialSecretGenerator
import doa.ink.workbench.core.identity.auth.SessionAuthenticator
import doa.ink.workbench.core.identity.model.AuditEventResult
import doa.ink.workbench.core.identity.model.AuthEventType
import doa.ink.workbench.core.identity.model.AuthenticatedIdentity
import doa.ink.workbench.core.identity.model.AuthenticatedPrincipal
import doa.ink.workbench.core.identity.model.AuthenticationResult
import doa.ink.workbench.core.identity.model.CreateAuthEventCommand
import doa.ink.workbench.core.identity.model.CreateAuthSessionCommand
import doa.ink.workbench.core.identity.model.CreateBearerTokenCommand
import doa.ink.workbench.core.identity.model.IssuedCredential
import doa.ink.workbench.core.identity.model.LoginCommand
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service

@Service
@Suppress("TooManyFunctions")
class AuthenticationService(
  private val users: UserRepository,
  private val loginAccounts: LoginAccountRepository,
  private val authEvents: AuthEventRepository,
  private val sessions: AuthSessionRepository,
  private val bearerTokens: BearerTokenRepository,
  private val loginOrchestrator: LoginOrchestrator,
  private val secretGenerator: CredentialSecretGenerator,
  private val credentialHasher: CredentialHasher,
  private val clock: Clock,
) : SessionAuthenticator, BearerTokenAuthenticator {
  private val defaultSessionTtl = Duration.ofHours(12)
  private val defaultBearerTokenTtl = Duration.ofDays(30)

  suspend fun login(command: LoginCommand): AuthenticationResult {
    val identity =
      try {
        loginOrchestrator.authenticate(command)
      } catch (error: AuthenticationFailedException) {
        recordLoginFailure(command, error.message)
        throw error
      }

    return completeLogin(identity, command.issueBearerToken, command.ipAddress, command.userAgent)
  }

  suspend fun completeLogin(
    identity: AuthenticatedIdentity,
    issueBearerToken: Boolean,
    ipAddress: String?,
    userAgent: String?,
    tenantIdForAudit: UUID? = null,
  ): AuthenticationResult {
    val now = now()
    val session = issueSession(identity.user.id, identity.loginAccount.id, now)
    val bearerToken =
      if (issueBearerToken) issueBearerToken(identity.user.id, identity.loginAccount.id, now) else null
    loginAccounts.touchLastUsed(identity.loginAccount.id, now)
    authEvents.append(
      CreateAuthEventCommand(
        tenantId = tenantIdForAudit,
        userId = identity.user.id,
        loginAccountId = identity.loginAccount.id,
        loginMethodId = identity.loginAccount.loginMethodId,
        eventType = AuthEventType.LOGIN_SUCCESS,
        result = AuditEventResult.SUCCESS,
        ipAddress = ipAddress,
        userAgent = userAgent,
      )
    )
    return AuthenticationResult(
      principal =
        AuthenticatedPrincipal(
          user = identity.user,
          loginAccountId = identity.loginAccount.id,
          sessionId = session.id.toString(),
          bearerTokenId = bearerToken?.id?.toString(),
        ),
      session = session,
      bearerToken = bearerToken,
    )
  }

  suspend fun createBearerToken(
    userId: UUID,
    loginAccountId: UUID,
    ipAddress: String?,
    userAgent: String?,
  ): IssuedCredential {
    val user =
      users.findById(userId) ?: throw InvalidRequestException("Authenticated user not found.")
    val token = issueBearerToken(user.id, loginAccountId, now())
    authEvents.append(
      CreateAuthEventCommand(
        userId = user.id,
        loginAccountId = loginAccountId,
        eventType = AuthEventType.TOKEN_CREATED,
        result = AuditEventResult.SUCCESS,
        ipAddress = ipAddress,
        userAgent = userAgent,
      )
    )
    return token
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

  suspend fun revokeBearerToken(
    tokenSecret: String,
    ipAddress: String?,
    userAgent: String?,
  ): Boolean {
    val now = now()
    val token =
      bearerTokens.findActiveByHash(credentialHasher.hash(tokenSecret), now) ?: return false
    val revoked = bearerTokens.revoke(token.id, now)
    if (revoked) {
      authEvents.append(
        CreateAuthEventCommand(
          userId = token.userId,
          loginAccountId = token.loginAccountId,
          eventType = AuthEventType.TOKEN_REVOKED,
          result = AuditEventResult.SUCCESS,
          ipAddress = ipAddress,
          userAgent = userAgent,
        )
      )
    }
    return revoked
  }

  suspend fun revokeBearerTokenById(
    tokenId: UUID,
    actorUserId: UUID,
    ipAddress: String?,
    userAgent: String?,
  ): Boolean {
    val token = bearerTokens.findById(tokenId) ?: return false
    return if (token.userId != actorUserId) {
      false
    } else {
      val now = now()
      val revoked = bearerTokens.revoke(token.id, now)
      if (revoked) {
        authEvents.append(
          CreateAuthEventCommand(
            userId = token.userId,
            loginAccountId = token.loginAccountId,
            eventType = AuthEventType.TOKEN_REVOKED,
            result = AuditEventResult.SUCCESS,
            ipAddress = ipAddress,
            userAgent = userAgent,
          )
        )
      }
      revoked
    }
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
        )
      }
    }
  }

  override suspend fun authenticateBearerToken(token: String): AuthenticatedPrincipal? {
    val now = now()
    val bearerToken = bearerTokens.findActiveByHash(credentialHasher.hash(token), now)
    return bearerToken?.let {
      users.findById(it.userId)?.let { user ->
        bearerTokens.touch(it.id, now)
        loginAccounts.touchLastUsed(it.loginAccountId, now)
        AuthenticatedPrincipal(
          user = user,
          loginAccountId = it.loginAccountId,
          sessionId = null,
          bearerTokenId = it.id.toString(),
        )
      }
    }
  }

  private suspend fun issueSession(
    userId: UUID,
    loginAccountId: UUID,
    now: OffsetDateTime,
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
          activeTenantId = null,
        )
      )
    return IssuedCredential(session.id, secret, session.expiresAt)
  }

  private suspend fun issueBearerToken(
    userId: UUID,
    loginAccountId: UUID,
    now: OffsetDateTime,
  ): IssuedCredential {
    val secret = secretGenerator.generate()
    val tokenTtl = bearerTokenTtlForUser(userId)
    val token =
      bearerTokens.create(
        CreateBearerTokenCommand(
          tokenHash = credentialHasher.hash(secret),
          userId = userId,
          loginAccountId = loginAccountId,
          expiresAt = now.plus(tokenTtl),
        )
      )
    return IssuedCredential(token.id, secret, token.expiresAt)
  }

  private suspend fun sessionTtlForUser(userId: UUID): Duration {
    val memberships = users.findById(userId) ?: return defaultSessionTtl
    return defaultSessionTtl
  }

  private suspend fun bearerTokenTtlForUser(userId: UUID): Duration {
    users.findById(userId)
    return defaultBearerTokenTtl
  }

  private suspend fun recordLoginFailure(command: LoginCommand, reason: String?) {
    authEvents.append(
      CreateAuthEventCommand(
        tenantId = null,
        eventType = AuthEventType.LOGIN_FAILURE,
        result = AuditEventResult.FAILURE,
        failureReason = reason ?: "invalid_credentials",
        ipAddress = command.ipAddress,
        userAgent = command.userAgent,
      )
    )
  }

  private fun now(): OffsetDateTime = OffsetDateTime.now(clock)
}
