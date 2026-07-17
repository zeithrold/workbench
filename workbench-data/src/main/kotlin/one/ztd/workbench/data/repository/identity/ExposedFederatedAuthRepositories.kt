package one.ztd.workbench.data.repository.identity

import java.time.OffsetDateTime
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import one.ztd.workbench.data.persistence.postgres.identity.AuthLoginStatesTable
import one.ztd.workbench.data.persistence.postgres.identity.MagicLinkTokensTable
import one.ztd.workbench.identity.auth.AuthLoginStateRepository
import one.ztd.workbench.identity.auth.MagicLinkTokenRepository
import one.ztd.workbench.identity.model.AuthLoginStateRecord
import one.ztd.workbench.identity.model.CreateAuthLoginStateCommand
import one.ztd.workbench.identity.model.MagicLinkTokenRecord
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

@Repository
class ExposedAuthLoginStateRepository(private val database: Database) : AuthLoginStateRepository {
  override suspend fun create(command: CreateAuthLoginStateCommand): AuthLoginStateRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val now = nowUtc()
      AuthLoginStatesTable.insert {
        it[AuthLoginStatesTable.id] = id.toKotlinUuid()
        it[stateHash] = command.stateHash
        it[tenantId] = command.tenantId.toKotlinUuid()
        it[loginMethodId] = command.loginMethodId.toKotlinUuid()
        it[redirectUri] = command.redirectUri
        it[pkceVerifier] = command.pkceVerifier
        it[returnUrl] = command.returnUrl
        it[expiresAt] = command.expiresAt
        it[createdAt] = now
      }
      AuthLoginStatesTable.selectAll()
        .where { AuthLoginStatesTable.id eq id.toKotlinUuid() }
        .single()
        .toAuthLoginStateRecord()
    }

  override suspend fun findActiveByStateHash(
    stateHash: String,
    now: OffsetDateTime,
  ): AuthLoginStateRecord? =
    suspendTransaction(db = database) {
      AuthLoginStatesTable.selectAll()
        .where {
          (AuthLoginStatesTable.stateHash eq stateHash) and AuthLoginStatesTable.consumedAt.isNull()
        }
        .singleOrNull()
        ?.toAuthLoginStateRecord()
        ?.takeIf { it.expiresAt.isAfter(now) }
    }

  override suspend fun consume(id: UUID, consumedAt: OffsetDateTime): Boolean =
    suspendTransaction(db = database) {
      AuthLoginStatesTable.update({
        (AuthLoginStatesTable.id eq id.toKotlinUuid()) and AuthLoginStatesTable.consumedAt.isNull()
      }) {
        it[AuthLoginStatesTable.consumedAt] = consumedAt
      } > 0
    }
}

@Repository
class ExposedMagicLinkTokenRepository(private val database: Database) : MagicLinkTokenRepository {
  override suspend fun create(
    tokenHash: String,
    loginMethodId: UUID,
    tenantId: UUID,
    normalizedSubject: String,
    expiresAt: OffsetDateTime,
  ): MagicLinkTokenRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val now = nowUtc()
      MagicLinkTokensTable.insert {
        it[MagicLinkTokensTable.id] = id.toKotlinUuid()
        it[MagicLinkTokensTable.tokenHash] = tokenHash
        it[MagicLinkTokensTable.loginMethodId] = loginMethodId.toKotlinUuid()
        it[MagicLinkTokensTable.tenantId] = tenantId.toKotlinUuid()
        it[MagicLinkTokensTable.normalizedSubject] = normalizedSubject
        it[MagicLinkTokensTable.expiresAt] = expiresAt
        it[MagicLinkTokensTable.createdAt] = now
      }
      MagicLinkTokensTable.selectAll()
        .where { MagicLinkTokensTable.id eq id.toKotlinUuid() }
        .single()
        .toMagicLinkTokenRecord()
    }

  override suspend fun findActiveByHash(
    tokenHash: String,
    now: OffsetDateTime,
  ): MagicLinkTokenRecord? =
    suspendTransaction(db = database) {
      MagicLinkTokensTable.selectAll()
        .where {
          (MagicLinkTokensTable.tokenHash eq tokenHash) and MagicLinkTokensTable.consumedAt.isNull()
        }
        .singleOrNull()
        ?.toMagicLinkTokenRecord()
        ?.takeIf { it.expiresAt.isAfter(now) }
    }

  override suspend fun consume(id: UUID, consumedAt: OffsetDateTime): Boolean =
    suspendTransaction(db = database) {
      MagicLinkTokensTable.update({
        (MagicLinkTokensTable.id eq id.toKotlinUuid()) and MagicLinkTokensTable.consumedAt.isNull()
      }) {
        it[MagicLinkTokensTable.consumedAt] = consumedAt
      } > 0
    }
}
