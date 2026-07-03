package doa.ink.workbench.data.identity

import doa.ink.workbench.core.common.ids.PublicId
import doa.ink.workbench.core.identity.InvitationRepository
import doa.ink.workbench.core.identity.model.CreateInvitationCommand
import doa.ink.workbench.core.identity.model.InvitationRecord
import doa.ink.workbench.data.persistence.InvitationsTable
import java.time.OffsetDateTime
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
class ExposedInvitationRepository(private val database: Database) : InvitationRepository {
  override suspend fun create(command: CreateInvitationCommand): InvitationRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val apiId = PublicId.new("inv")
      val now = nowUtc()
      InvitationsTable.insert {
        it[InvitationsTable.id] = id.toKotlinUuid()
        it[InvitationsTable.apiId] = apiId.value
        it[invitationType] = command.type.dbValue
        it[tenantId] = command.tenantId.toKotlinUuid()
        it[email] = command.email
        it[normalizedEmail] = command.normalizedEmail
        it[displayName] = command.displayName
        it[tokenHash] = command.tokenHash
        it[invitedBy] = command.invitedBy.toKotlinUuid()
        it[expiresAt] = command.expiresAt
        it[createdAt] = now
      }
      InvitationsTable.selectAll()
        .where { InvitationsTable.id eq id.toKotlinUuid() }
        .single()
        .toInvitationRecord()
    }

  override suspend fun findActiveByHash(tokenHash: String, now: OffsetDateTime): InvitationRecord? =
    suspendTransaction(db = database) {
      InvitationsTable.selectAll()
        .where {
          (InvitationsTable.tokenHash eq tokenHash) and InvitationsTable.consumedAt.isNull()
        }
        .singleOrNull()
        ?.toInvitationRecord()
        ?.takeIf { it.expiresAt.isAfter(now) }
    }

  override suspend fun consume(id: UUID, consumedAt: OffsetDateTime): Boolean =
    suspendTransaction(db = database) {
      InvitationsTable.update({
        (InvitationsTable.id eq id.toKotlinUuid()) and InvitationsTable.consumedAt.isNull()
      }) {
        it[InvitationsTable.consumedAt] = consumedAt
      } > 0
    }
}
