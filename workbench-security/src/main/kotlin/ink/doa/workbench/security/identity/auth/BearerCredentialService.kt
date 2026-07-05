package ink.doa.workbench.security.identity.auth

import ink.doa.workbench.core.common.errors.AuthenticationFailedException
import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.identity.auth.BearerTokenAuthenticator
import ink.doa.workbench.core.identity.auth.BearerTokenRepository
import ink.doa.workbench.core.identity.model.AuditEventResult
import ink.doa.workbench.core.identity.model.AuthEventType
import ink.doa.workbench.core.identity.model.AuthenticatedPrincipal
import ink.doa.workbench.core.identity.model.CreateAuthEventCommand
import ink.doa.workbench.core.identity.model.CreateBearerTokenCommand
import ink.doa.workbench.core.identity.model.CredentialType
import ink.doa.workbench.core.identity.model.IssuedCredential
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class BearerCredentialService(
  private val credentials: AuthCredentialSupport,
  private val crypto: CredentialCryptoSupport,
  private val bearerTokens: BearerTokenRepository,
  private val clock: Clock,
) : BearerTokenAuthenticator {
  private val defaultBearerTokenTtl = Duration.ofDays(30)

  suspend fun issueBearerToken(command: IssueBearerTokenCommand): IssuedCredential {
    val secret = crypto.secretGenerator.generate()
    val tokenTtl = bearerTokenTtlForUser(command.userId)
    val token =
      bearerTokens.create(
        CreateBearerTokenCommand(
          tokenHash = crypto.credentialHasher.hash(secret),
          userId = command.userId,
          loginAccountId = command.loginAccountId,
          expiresAt = command.now.plus(tokenTtl),
          tenantId = command.tenantId,
          name = command.name,
          scopes = command.scopes,
          createdBy = command.createdBy,
        )
      )
    return IssuedCredential(token.id, token.apiId, secret, token.expiresAt)
  }

  suspend fun createBearerToken(command: CreateManagedBearerTokenCommand): IssuedCredential {
    val user =
      credentials.users.findById(command.userId)
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND,
          "Authenticated user not found.",
        )
    val token =
      issueBearerToken(
        IssueBearerTokenCommand(
          userId = user.id,
          loginAccountId = command.loginAccountId,
          tenantId = command.tenantId,
          name = command.name,
          scopes = command.scopes.ifEmpty { setOf("workbench.api") },
          createdBy = user.id,
          now = now(),
        )
      )
    credentials.authEvents.append(
      CreateAuthEventCommand(
        userId = user.id,
        loginAccountId = command.loginAccountId,
        eventType = AuthEventType.TOKEN_CREATED,
        result = AuditEventResult.SUCCESS,
        ipAddress = command.ipAddress,
        userAgent = command.userAgent,
      )
    )
    return token
  }

  suspend fun revokeBearerToken(
    tokenSecret: String,
    ipAddress: String?,
    userAgent: String?,
  ): Boolean {
    val now = now()
    val token =
      bearerTokens.findActiveByHash(crypto.credentialHasher.hash(tokenSecret), now) ?: return false
    return revokeTokenRecord(token.id, token.userId, token.loginAccountId, ipAddress, userAgent)
  }

  suspend fun revokeBearerTokenByApiId(
    tokenApiId: String,
    actorUserId: UUID,
    ipAddress: String?,
    userAgent: String?,
  ): Boolean {
    val token =
      bearerTokens.findByApiId(tokenApiId)
        ?: throw AuthenticationFailedException(WorkbenchErrorCode.AUTH_TOKEN_NOT_FOUND)
    return revokeBearerTokenById(token.id, actorUserId, ipAddress, userAgent)
  }

  suspend fun revokeBearerTokenById(
    tokenId: UUID,
    actorUserId: UUID,
    ipAddress: String?,
    userAgent: String?,
  ): Boolean {
    val token = bearerTokens.findById(tokenId) ?: return false
    if (token.userId != actorUserId) return false
    return revokeTokenRecord(token.id, token.userId, token.loginAccountId, ipAddress, userAgent)
  }

  override suspend fun authenticateBearerToken(token: String): AuthenticatedPrincipal? {
    val now = now()
    val bearerToken = bearerTokens.findActiveByHash(crypto.credentialHasher.hash(token), now)
    return bearerToken?.let {
      credentials.users.findById(it.userId)?.let { user ->
        bearerTokens.touch(it.id, now)
        credentials.loginAccounts.touchLastUsed(it.loginAccountId, now)
        AuthenticatedPrincipal(
          user = user,
          loginAccountId = it.loginAccountId,
          sessionId = null,
          bearerTokenId = it.id.toString(),
          credentialType = CredentialType.BEARER_TOKEN,
          tenantId = it.tenantId,
          credentialScopes = it.scopes,
        )
      }
    }
  }

  private suspend fun revokeTokenRecord(
    tokenId: UUID,
    userId: UUID,
    loginAccountId: UUID,
    ipAddress: String?,
    userAgent: String?,
  ): Boolean {
    val now = now()
    val revoked = bearerTokens.revoke(tokenId, now)
    if (revoked) {
      credentials.authEvents.append(
        CreateAuthEventCommand(
          userId = userId,
          loginAccountId = loginAccountId,
          eventType = AuthEventType.TOKEN_REVOKED,
          result = AuditEventResult.SUCCESS,
          ipAddress = ipAddress,
          userAgent = userAgent,
        )
      )
    }
    return revoked
  }

  private suspend fun bearerTokenTtlForUser(userId: UUID): Duration {
    credentials.users.findById(userId)
    return defaultBearerTokenTtl
  }

  private fun now(): OffsetDateTime = OffsetDateTime.now(clock)
}
