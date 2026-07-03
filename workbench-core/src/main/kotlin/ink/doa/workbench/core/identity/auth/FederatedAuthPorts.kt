package ink.doa.workbench.core.identity.auth

import ink.doa.workbench.core.identity.model.AuthLoginStateRecord
import ink.doa.workbench.core.identity.model.CreateAuthLoginStateCommand
import ink.doa.workbench.core.identity.model.MagicLinkTokenRecord
import java.time.OffsetDateTime
import java.util.UUID

interface AuthLoginStateRepository {
  suspend fun create(command: CreateAuthLoginStateCommand): AuthLoginStateRecord

  suspend fun findActiveByStateHash(stateHash: String, now: OffsetDateTime): AuthLoginStateRecord?

  suspend fun consume(id: UUID, consumedAt: OffsetDateTime): Boolean
}

interface MagicLinkTokenRepository {
  suspend fun create(
    tokenHash: String,
    loginMethodId: UUID,
    tenantId: UUID,
    normalizedSubject: String,
    expiresAt: OffsetDateTime,
  ): MagicLinkTokenRecord

  suspend fun findActiveByHash(tokenHash: String, now: OffsetDateTime): MagicLinkTokenRecord?

  suspend fun consume(id: UUID, consumedAt: OffsetDateTime): Boolean
}

interface SecretResolver {
  fun resolve(secretRef: String): String?
}
