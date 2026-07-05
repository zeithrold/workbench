package ink.doa.workbench.data.identity

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.UserRepository
import ink.doa.workbench.core.identity.model.CreateUserCommand
import ink.doa.workbench.core.identity.model.UserRecord
import ink.doa.workbench.data.persistence.UsersTable
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.springframework.stereotype.Repository

@Repository
class ExposedUserRepository(private val database: Database) : UserRepository {
  override suspend fun create(command: CreateUserCommand): UserRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val apiId = PublicId.new("usr")
      val now = nowUtc()
      UsersTable.insert {
        it[UsersTable.id] = id.toKotlinUuid()
        it[UsersTable.apiId] = apiId.value
        it[UsersTable.displayName] = command.displayName
        it[UsersTable.primaryEmail] = command.primaryEmail
        it[UsersTable.avatarUrl] = command.avatarUrl
        it[UsersTable.timezone] = command.timezone
        it[UsersTable.locale] = command.locale
        it[UsersTable.createdAt] = now
        it[UsersTable.updatedAt] = now
      }
      UsersTable.selectAll()
        .where { (UsersTable.deletedAt.isNull()) and (UsersTable.id eq id.toKotlinUuid()) }
        .single()
        .toUserRecord()
    }

  override suspend fun findById(id: UUID): UserRecord? =
    suspendTransaction(db = database) {
      UsersTable.selectAll()
        .where { (UsersTable.deletedAt.isNull()) and (UsersTable.id eq id.toKotlinUuid()) }
        .singleOrNull()
        ?.toUserRecord()
    }

  override suspend fun findByApiId(apiId: String): UserRecord? =
    suspendTransaction(db = database) {
      UsersTable.selectAll()
        .where { (UsersTable.deletedAt.isNull()) and (UsersTable.apiId eq apiId) }
        .singleOrNull()
        ?.toUserRecord()
    }

  override suspend fun findByPrimaryEmail(primaryEmail: String): UserRecord? =
    suspendTransaction(db = database) {
      UsersTable.selectAll()
        .where { (UsersTable.deletedAt.isNull()) and (UsersTable.primaryEmail eq primaryEmail) }
        .singleOrNull()
        ?.toUserRecord()
    }
}
