package ink.doa.workbench.data.repository.identity

import ink.doa.workbench.data.persistence.postgres.event.AuthEventsTable
import ink.doa.workbench.identity.AuthEventRepository
import ink.doa.workbench.identity.model.AuthEventRecord
import ink.doa.workbench.identity.model.CreateAuthEventCommand
import ink.doa.workbench.kernel.common.ids.PublicId
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.springframework.stereotype.Repository

@Repository
class ExposedAuthEventRepository(private val database: Database) : AuthEventRepository {
  override suspend fun append(command: CreateAuthEventCommand): AuthEventRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val now = nowUtc()
      AuthEventsTable.insert {
        it[AuthEventsTable.id] = id.toKotlinUuid()
        it[AuthEventsTable.authEventId] = PublicId.new("aut").value
        it[AuthEventsTable.tenantId] = command.tenantId?.toKotlinUuid()
        it[AuthEventsTable.userId] = command.userId?.toKotlinUuid()
        it[AuthEventsTable.loginAccountId] = command.loginAccountId?.toKotlinUuid()
        it[AuthEventsTable.loginMethodId] = command.loginMethodId?.toKotlinUuid()
        it[AuthEventsTable.eventType] = command.eventType.dbValue
        it[AuthEventsTable.result] = command.result.dbValue
        it[AuthEventsTable.failureReason] = command.failureReason
        it[AuthEventsTable.ipAddress] = command.ipAddress
        it[AuthEventsTable.userAgent] = command.userAgent
        it[AuthEventsTable.metadata] = command.metadata
        it[AuthEventsTable.occurredAt] = now
      }
      AuthEventsTable.selectAll()
        .where { AuthEventsTable.id eq id.toKotlinUuid() }
        .single()
        .toAuthEventRecord()
    }

  override suspend fun listRecentByUser(userId: UUID, limit: Int): List<AuthEventRecord> =
    suspendTransaction(db = database) {
      AuthEventsTable.selectAll()
        .where { AuthEventsTable.userId eq userId.toKotlinUuid() }
        .orderBy(AuthEventsTable.occurredAt, SortOrder.DESC)
        .limit(limit)
        .map { it.toAuthEventRecord() }
    }

  override suspend fun listRecentByLoginAccount(
    loginAccountId: UUID,
    limit: Int,
  ): List<AuthEventRecord> =
    suspendTransaction(db = database) {
      AuthEventsTable.selectAll()
        .where { AuthEventsTable.loginAccountId eq loginAccountId.toKotlinUuid() }
        .orderBy(AuthEventsTable.occurredAt, SortOrder.DESC)
        .limit(limit)
        .map { it.toAuthEventRecord() }
    }
}
