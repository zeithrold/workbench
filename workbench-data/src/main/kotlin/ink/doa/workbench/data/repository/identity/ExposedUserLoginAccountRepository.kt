package ink.doa.workbench.data.repository.identity

import ink.doa.workbench.data.persistence.postgres.identity.UserLoginAccountsTable
import ink.doa.workbench.data.persistence.postgres.identity.UsersTable
import ink.doa.workbench.identity.UserLoginAccountRepository
import ink.doa.workbench.identity.model.LinkUserLoginAccountCommand
import ink.doa.workbench.identity.model.UserLoginAccountRecord
import ink.doa.workbench.identity.model.UserRecord
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.uuid.toJavaUuid
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
class ExposedUserLoginAccountRepository(private val database: Database) :
  UserLoginAccountRepository {
  override suspend fun linkUser(command: LinkUserLoginAccountCommand): UserLoginAccountRecord =
    suspendTransaction(db = database) {
      val now = nowUtc()
      val existing =
        UserLoginAccountsTable.selectAll()
          .where { UserLoginAccountsTable.loginAccountId eq command.loginAccountId.toKotlinUuid() }
          .singleOrNull()
      val id = existing?.get(UserLoginAccountsTable.id)?.toJavaUuid() ?: UUID.randomUUID()

      if (existing == null) {
        UserLoginAccountsTable.insert {
          it[UserLoginAccountsTable.id] = id.toKotlinUuid()
          it[UserLoginAccountsTable.userId] = command.userId.toKotlinUuid()
          it[UserLoginAccountsTable.loginAccountId] = command.loginAccountId.toKotlinUuid()
          it[UserLoginAccountsTable.linkedBy] = command.linkedBy?.toKotlinUuid()
          it[UserLoginAccountsTable.linkedAt] = now
        }
      } else {
        UserLoginAccountsTable.update({ UserLoginAccountsTable.id eq id.toKotlinUuid() }) {
          it[UserLoginAccountsTable.userId] = command.userId.toKotlinUuid()
          it[UserLoginAccountsTable.linkedBy] = command.linkedBy?.toKotlinUuid()
          it[UserLoginAccountsTable.linkedAt] = now
          it[UserLoginAccountsTable.unlinkedAt] = null
        }
      }

      UserLoginAccountsTable.selectAll()
        .where { UserLoginAccountsTable.id eq id.toKotlinUuid() }
        .single()
        .toUserLoginAccountRecord()
    }

  override suspend fun unlink(loginAccountId: UUID, unlinkedAt: OffsetDateTime): Boolean =
    suspendTransaction(db = database) {
      UserLoginAccountsTable.update({
        (UserLoginAccountsTable.loginAccountId eq loginAccountId.toKotlinUuid()) and
          UserLoginAccountsTable.unlinkedAt.isNull()
      }) {
        it[UserLoginAccountsTable.unlinkedAt] = unlinkedAt
      } > 0
    }

  override suspend fun findLinkedUser(loginAccountId: UUID): UserRecord? =
    suspendTransaction(db = database) {
      val link =
        UserLoginAccountsTable.selectAll()
          .where {
            (UserLoginAccountsTable.loginAccountId eq loginAccountId.toKotlinUuid()) and
              UserLoginAccountsTable.unlinkedAt.isNull()
          }
          .singleOrNull() ?: return@suspendTransaction null
      UsersTable.selectAll()
        .where {
          (UsersTable.deletedAt.isNull()) and (UsersTable.id eq link[UserLoginAccountsTable.userId])
        }
        .singleOrNull()
        ?.toUserRecord()
    }
}
