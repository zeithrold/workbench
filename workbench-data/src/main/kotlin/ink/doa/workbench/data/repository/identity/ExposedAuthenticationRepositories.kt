package ink.doa.workbench.data.repository.identity

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.auth.AuthSessionRepository
import ink.doa.workbench.core.identity.auth.BearerTokenRepository
import ink.doa.workbench.core.identity.model.AuthSessionRecord
import ink.doa.workbench.core.identity.model.BearerTokenRecord
import ink.doa.workbench.core.identity.model.CreateAuthSessionCommand
import ink.doa.workbench.core.identity.model.CreateBearerTokenCommand
import ink.doa.workbench.data.persistence.postgres.identity.AuthSessionsTable
import ink.doa.workbench.data.persistence.postgres.identity.BearerTokensTable
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
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
        it[activeTenantId] = command.activeTenantId?.toKotlinUuid()
        it[expiresAt] = command.expiresAt
        it[createdAt] = now
        it[updatedAt] = now
      }
      AuthSessionsTable.selectAll()
        .where { AuthSessionsTable.id eq id.toKotlinUuid() }
        .single()
        .toAuthSessionRecord()
    }

  override suspend fun findById(id: UUID): AuthSessionRecord? =
    suspendTransaction(db = database) {
      AuthSessionsTable.selectAll()
        .where { AuthSessionsTable.id eq id.toKotlinUuid() }
        .singleOrNull()
        ?.toAuthSessionRecord()
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

  override suspend fun updateActiveTenant(
    id: UUID,
    activeTenantId: UUID?,
    updatedAt: OffsetDateTime,
  ): Boolean =
    suspendTransaction(db = database) {
      AuthSessionsTable.update({
        (AuthSessionsTable.id eq id.toKotlinUuid()) and AuthSessionsTable.revokedAt.isNull()
      }) {
        it[AuthSessionsTable.activeTenantId] = activeTenantId?.toKotlinUuid()
        it[AuthSessionsTable.updatedAt] = updatedAt
      } > 0
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

  override suspend fun revokeByActiveTenant(tenantId: UUID, revokedAt: OffsetDateTime): Int =
    suspendTransaction(db = database) {
      AuthSessionsTable.update({
        (AuthSessionsTable.activeTenantId eq tenantId.toKotlinUuid()) and
          AuthSessionsTable.revokedAt.isNull()
      }) {
        it[AuthSessionsTable.revokedAt] = revokedAt
        it[AuthSessionsTable.updatedAt] = revokedAt
      }
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
      val apiId = PublicId.new("btk")
      val now = nowUtc()
      BearerTokensTable.insert {
        it[BearerTokensTable.id] = id.toKotlinUuid()
        it[BearerTokensTable.apiId] = apiId.value
        it[tokenHash] = command.tokenHash
        it[userId] = command.userId.toKotlinUuid()
        it[loginAccountId] = command.loginAccountId.toKotlinUuid()
        it[tenantId] = command.tenantId?.toKotlinUuid()
        it[name] = command.name
        it[scopes] = JsonArray(command.scopes.sorted().map { scope -> JsonPrimitive(scope) })
        it[createdBy] = command.createdBy?.toKotlinUuid()
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

  override suspend fun findByApiId(apiId: String): BearerTokenRecord? =
    suspendTransaction(db = database) {
      BearerTokensTable.selectAll()
        .where { BearerTokensTable.apiId eq apiId }
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

  override suspend fun revokeByTenant(tenantId: UUID, revokedAt: OffsetDateTime): Int =
    suspendTransaction(db = database) {
      BearerTokensTable.update({
        (BearerTokensTable.tenantId eq tenantId.toKotlinUuid()) and
          BearerTokensTable.revokedAt.isNull()
      }) {
        it[BearerTokensTable.revokedAt] = revokedAt
        it[BearerTokensTable.updatedAt] = revokedAt
      }
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
