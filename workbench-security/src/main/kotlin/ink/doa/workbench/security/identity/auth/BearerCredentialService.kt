package ink.doa.workbench.security.identity.auth

import ink.doa.workbench.core.common.errors.AuthenticationFailedException
import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.identity.AuthEventRepository
import ink.doa.workbench.core.identity.LoginAccountStore
import ink.doa.workbench.core.identity.UserRepository
import ink.doa.workbench.core.identity.auth.BearerTokenAuthenticator
import ink.doa.workbench.core.identity.auth.BearerTokenRepository
import ink.doa.workbench.core.identity.auth.CredentialHasher
import ink.doa.workbench.core.identity.auth.CredentialSecretGenerator
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
  private val users: UserRepository,
  private val loginAccounts: LoginAccountStore,
  private val authEvents: AuthEventRepository,
  private val bearerTokens: BearerTokenRepository,
  private val secretGenerator: CredentialSecretGenerator,
  private val credentialHasher: CredentialHasher,
  private val clock: Clock,
) : BearerTokenAuthenticator {
  private val defaultBearerTokenTtl = Duration.ofDays(30)

  suspend fun issueBearerToken(
    userId: UUID,
    loginAccountId: UUID,
    tenantId: UUID?,
    name: String?,
    scopes: Set<String>,
    createdBy: UUID?,
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
          tenantId = tenantId,
          name = name,
          scopes = scopes,
          createdBy = createdBy,
        )
      )
    return IssuedCredential(token.id, token.apiId, secret, token.expiresAt)
  }

  suspend fun createBearerToken(
    userId: UUID,
    loginAccountId: UUID,
    tenantId: UUID?,
    name: String?,
    scopes: Set<String>,
    ipAddress: String?,
    userAgent: String?,
  ): IssuedCredential {
    val user =
      users.findById(userId)
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND,
          "Authenticated user not found.",
        )
    val token =
      issueBearerToken(
        userId = user.id,
        loginAccountId = loginAccountId,
        tenantId = tenantId,
        name = name,
        scopes = scopes.ifEmpty { setOf("workbench.api") },
        createdBy = user.id,
        now = now(),
      )
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

  suspend fun revokeBearerToken(
    tokenSecret: String,
    ipAddress: String?,
    userAgent: String?,
  ): Boolean {
    val now = now()
    val token =
      bearerTokens.findActiveByHash(credentialHasher.hash(tokenSecret), now) ?: return false
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
      authEvents.append(
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
    users.findById(userId)
    return defaultBearerTokenTtl
  }

  private fun now(): OffsetDateTime = OffsetDateTime.now(clock)
}
