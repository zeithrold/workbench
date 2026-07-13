package ink.doa.workbench.data.repository.identity

import ink.doa.workbench.data.persistence.postgres.identity.LoginMethodDefinitionsTable
import ink.doa.workbench.identity.LoginMethodRepository
import ink.doa.workbench.identity.model.CreateLoginMethodDefinitionCommand
import ink.doa.workbench.identity.model.LoginMethodDefinitionRecord
import ink.doa.workbench.kernel.common.ids.PublicId
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.springframework.stereotype.Repository

@Repository
class ExposedLoginMethodRepository(private val database: Database) : LoginMethodRepository {
  override suspend fun createLoginMethod(
    command: CreateLoginMethodDefinitionCommand
  ): LoginMethodDefinitionRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val apiId = PublicId.new("lmg")
      val now = nowUtc()
      LoginMethodDefinitionsTable.insert {
        it[LoginMethodDefinitionsTable.id] = id.toKotlinUuid()
        it[LoginMethodDefinitionsTable.apiId] = apiId.value
        it[LoginMethodDefinitionsTable.code] = command.code
        it[LoginMethodDefinitionsTable.kind] = command.kind.dbValue
        it[LoginMethodDefinitionsTable.name] = command.name
        it[LoginMethodDefinitionsTable.isBuiltin] = command.isBuiltin
        it[LoginMethodDefinitionsTable.isEnabledGlobally] = command.isEnabledGlobally
        it[LoginMethodDefinitionsTable.configSchema] = command.configSchema
        it[LoginMethodDefinitionsTable.createdAt] = now
        it[LoginMethodDefinitionsTable.updatedAt] = now
      }
      LoginMethodDefinitionsTable.selectAll()
        .where { LoginMethodDefinitionsTable.id eq id.toKotlinUuid() }
        .single()
        .toLoginMethodDefinitionRecord()
    }

  override suspend fun findLoginMethodByCode(code: String): LoginMethodDefinitionRecord? =
    suspendTransaction(db = database) {
      LoginMethodDefinitionsTable.selectAll()
        .where {
          (LoginMethodDefinitionsTable.code eq code) and
            LoginMethodDefinitionsTable.isEnabledGlobally
        }
        .singleOrNull()
        ?.toLoginMethodDefinitionRecord()
    }

  override suspend fun findLoginMethodByApiId(apiId: String): LoginMethodDefinitionRecord? =
    suspendTransaction(db = database) {
      LoginMethodDefinitionsTable.selectAll()
        .where {
          (LoginMethodDefinitionsTable.apiId eq apiId) and
            LoginMethodDefinitionsTable.isEnabledGlobally
        }
        .singleOrNull()
        ?.toLoginMethodDefinitionRecord()
    }

  override suspend fun findLoginMethodById(id: UUID): LoginMethodDefinitionRecord? =
    suspendTransaction(db = database) {
      LoginMethodDefinitionsTable.selectAll()
        .where {
          (LoginMethodDefinitionsTable.id eq id.toKotlinUuid()) and
            LoginMethodDefinitionsTable.isEnabledGlobally
        }
        .singleOrNull()
        ?.toLoginMethodDefinitionRecord()
    }
}
