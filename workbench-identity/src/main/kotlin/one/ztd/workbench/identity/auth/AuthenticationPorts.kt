package one.ztd.workbench.identity.auth

import java.time.OffsetDateTime
import java.util.UUID
import one.ztd.workbench.identity.model.AuthSessionRecord
import one.ztd.workbench.identity.model.AuthenticatedPrincipal
import one.ztd.workbench.identity.model.BearerTokenRecord
import one.ztd.workbench.identity.model.CreateAuthSessionCommand
import one.ztd.workbench.identity.model.CreateBearerTokenCommand

interface SessionAuthenticator {
  suspend fun authenticateSession(sessionId: String): AuthenticatedPrincipal?
}

interface BearerTokenAuthenticator {
  suspend fun authenticateBearerToken(token: String): AuthenticatedPrincipal?
}

interface PasswordVerifier {
  fun verify(rawPassword: String, passwordHash: String): Boolean
}

interface PasswordHasher {
  fun hash(rawPassword: String): String
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

  suspend fun revokeByActiveTenant(tenantId: UUID, revokedAt: OffsetDateTime): Int

  suspend fun touch(id: UUID, usedAt: OffsetDateTime): Boolean
}

interface BearerTokenRepository {
  suspend fun create(command: CreateBearerTokenCommand): BearerTokenRecord

  suspend fun findById(id: UUID): BearerTokenRecord?

  suspend fun findByApiId(apiId: String): BearerTokenRecord?

  suspend fun findActiveByHash(tokenHash: String, now: OffsetDateTime): BearerTokenRecord?

  suspend fun revoke(id: UUID, revokedAt: OffsetDateTime): Boolean

  suspend fun revokeByTenant(tenantId: UUID, revokedAt: OffsetDateTime): Int

  suspend fun touch(id: UUID, usedAt: OffsetDateTime): Boolean
}
