package doa.ink.workbench.core.identity.auth

import doa.ink.workbench.core.identity.model.AuthSessionRecord
import doa.ink.workbench.core.identity.model.AuthenticatedPrincipal
import doa.ink.workbench.core.identity.model.BearerTokenRecord
import doa.ink.workbench.core.identity.model.CreateAuthSessionCommand
import doa.ink.workbench.core.identity.model.CreateBearerTokenCommand
import java.time.OffsetDateTime
import java.util.UUID

interface SessionAuthenticator {
  suspend fun authenticateSession(sessionId: String): AuthenticatedPrincipal?
}

interface BearerTokenAuthenticator {
  suspend fun authenticateBearerToken(token: String): AuthenticatedPrincipal?
}

interface PasswordVerifier {
  fun verify(rawPassword: String, passwordHash: String): Boolean
}

interface CredentialSecretGenerator {
  fun generate(): String
}

interface CredentialHasher {
  fun hash(secret: String): String
}

interface AuthSessionRepository {
  suspend fun create(command: CreateAuthSessionCommand): AuthSessionRecord

  suspend fun findById(id: UUID): AuthSessionRecord?

  suspend fun findActiveByHash(sessionHash: String, now: OffsetDateTime): AuthSessionRecord?

  suspend fun updateActiveTenant(
    id: UUID,
    activeTenantId: UUID?,
    updatedAt: OffsetDateTime,
  ): Boolean

  suspend fun revoke(id: UUID, revokedAt: OffsetDateTime): Boolean

  suspend fun touch(id: UUID, usedAt: OffsetDateTime): Boolean
}

interface BearerTokenRepository {
  suspend fun create(command: CreateBearerTokenCommand): BearerTokenRecord

  suspend fun findById(id: UUID): BearerTokenRecord?

  suspend fun findByApiId(apiId: String): BearerTokenRecord?

  suspend fun findActiveByHash(tokenHash: String, now: OffsetDateTime): BearerTokenRecord?

  suspend fun revoke(id: UUID, revokedAt: OffsetDateTime): Boolean

  suspend fun touch(id: UUID, usedAt: OffsetDateTime): Boolean
}
