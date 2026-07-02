package doa.ink.workbench.data.identity

import doa.ink.workbench.core.identity.auth.AuthSessionRepository
import doa.ink.workbench.core.identity.auth.BearerTokenRepository
import doa.ink.workbench.core.identity.model.AuthSessionRecord
import doa.ink.workbench.core.identity.model.BearerTokenRecord
import doa.ink.workbench.core.identity.model.CreateAuthSessionCommand
import doa.ink.workbench.core.identity.model.CreateBearerTokenCommand
import doa.ink.workbench.data.persistence.AuthSessionsTable
import doa.ink.workbench.data.persistence.BearerTokensTable
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toKotlinUuid
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
class ExposedAuthSessionRepository(private val database: Database) : AuthSessionRepository {
  override suspend fun create(command: CreateAuthSessionCommand): AuthSessionRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val now = nowUtc()
      AuthSessionsTable.insert {
        it[AuthSessionsTable.id] = id.toKotlinUuid()
        it[sessionHash] = command.sessionHash
        it[userId] = command.userId.toKotlinUuid()
        it[loginAccountId] = command.loginAccountId.toKotlinUuid()
        it[expiresAt] = command.expiresAt
        it[createdAt] = now
        it[updatedAt] = now
      }
      AuthSessionsTable.selectAll()
        .where { AuthSessionsTable.id eq id.toKotlinUuid() }
        .single()
        .toAuthSessionRecord()
    }

  override suspend fun findActiveByHash(
    sessionHash: String,
    now: OffsetDateTime,
  ): AuthSessionRecord? =
    suspendTransaction(db = database) {
      AuthSessionsTable.selectAll()
        .where {
          (AuthSessionsTable.sessionHash eq sessionHash) and AuthSessionsTable.revokedAt.isNull()
        }
        .singleOrNull()
        ?.toAuthSessionRecord()
        ?.takeIf { it.expiresAt.isAfter(now) }
    }

  override suspend fun revoke(id: UUID, revokedAt: OffsetDateTime): Boolean =
    suspendTransaction(db = database) {
      AuthSessionsTable.update({
        (AuthSessionsTable.id eq id.toKotlinUuid()) and AuthSessionsTable.revokedAt.isNull()
      }) {
        it[AuthSessionsTable.revokedAt] = revokedAt
        it[AuthSessionsTable.updatedAt] = revokedAt
      } > 0
    }

  override suspend fun touch(id: UUID, usedAt: OffsetDateTime): Boolean =
    suspendTransaction(db = database) {
      AuthSessionsTable.update({
        (AuthSessionsTable.id eq id.toKotlinUuid()) and AuthSessionsTable.revokedAt.isNull()
      }) {
        it[AuthSessionsTable.lastUsedAt] = usedAt
        it[AuthSessionsTable.updatedAt] = usedAt
      } > 0
    }
}

@Repository
class ExposedBearerTokenRepository(private val database: Database) : BearerTokenRepository {
  override suspend fun create(command: CreateBearerTokenCommand): BearerTokenRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val now = nowUtc()
      BearerTokensTable.insert {
        it[BearerTokensTable.id] = id.toKotlinUuid()
        it[tokenHash] = command.tokenHash
        it[userId] = command.userId.toKotlinUuid()
        it[loginAccountId] = command.loginAccountId.toKotlinUuid()
        it[expiresAt] = command.expiresAt
        it[createdAt] = now
        it[updatedAt] = now
      }
      BearerTokensTable.selectAll()
        .where { BearerTokensTable.id eq id.toKotlinUuid() }
        .single()
        .toBearerTokenRecord()
    }

  override suspend fun findById(id: UUID): BearerTokenRecord? =
    suspendTransaction(db = database) {
      BearerTokensTable.selectAll()
        .where { BearerTokensTable.id eq id.toKotlinUuid() }
        .singleOrNull()
        ?.toBearerTokenRecord()
    }

  override suspend fun findActiveByHash(
    tokenHash: String,
    now: OffsetDateTime,
  ): BearerTokenRecord? =
    suspendTransaction(db = database) {
      BearerTokensTable.selectAll()
        .where {
          (BearerTokensTable.tokenHash eq tokenHash) and BearerTokensTable.revokedAt.isNull()
        }
        .singleOrNull()
        ?.toBearerTokenRecord()
        ?.takeIf { it.expiresAt.isAfter(now) }
    }

  override suspend fun revoke(id: UUID, revokedAt: OffsetDateTime): Boolean =
    suspendTransaction(db = database) {
      BearerTokensTable.update({
        (BearerTokensTable.id eq id.toKotlinUuid()) and BearerTokensTable.revokedAt.isNull()
      }) {
        it[BearerTokensTable.revokedAt] = revokedAt
        it[BearerTokensTable.updatedAt] = revokedAt
      } > 0
    }

  override suspend fun touch(id: UUID, usedAt: OffsetDateTime): Boolean =
    suspendTransaction(db = database) {
      BearerTokensTable.update({
        (BearerTokensTable.id eq id.toKotlinUuid()) and BearerTokensTable.revokedAt.isNull()
      }) {
        it[BearerTokensTable.lastUsedAt] = usedAt
        it[BearerTokensTable.updatedAt] = usedAt
      } > 0
    }
}

private fun nowUtc(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)
