package ink.doa.workbench.data.repository.identity

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.LoginAccountStore
import ink.doa.workbench.core.identity.model.CreateLoginAccountCommand
import ink.doa.workbench.core.identity.model.LoginAccountParameterKey
import ink.doa.workbench.core.identity.model.LoginAccountParameterRecord
import ink.doa.workbench.core.identity.model.LoginAccountRecord
import ink.doa.workbench.core.identity.model.UpsertLoginAccountParameterCommand
import ink.doa.workbench.data.persistence.postgres.identity.LoginAccountParametersTable
import ink.doa.workbench.data.persistence.postgres.identity.LoginAccountsTable
import ink.doa.workbench.data.persistence.postgres.identity.LoginMethodDefinitionsTable
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
class ExposedLoginAccountStore(private val database: Database) : LoginAccountStore {
  override suspend fun createLoginAccount(command: CreateLoginAccountCommand): LoginAccountRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val now = nowUtc()
      LoginAccountsTable.insert {
        it[LoginAccountsTable.id] = id.toKotlinUuid()
        it[LoginAccountsTable.apiId] = PublicId.new("lac").value
        it[LoginAccountsTable.loginMethodId] = command.loginMethodId.toKotlinUuid()
        it[LoginAccountsTable.subject] = command.subject
        it[LoginAccountsTable.normalizedSubject] = command.normalizedSubject
        it[LoginAccountsTable.displayName] = command.displayName
        it[LoginAccountsTable.createdAt] = now
        it[LoginAccountsTable.updatedAt] = now
      }
      LoginAccountsTable.selectAll()
        .where {
          (LoginAccountsTable.disabledAt.isNull()) and (LoginAccountsTable.id eq id.toKotlinUuid())
        }
        .single()
        .toLoginAccountRecord()
    }

  override suspend fun upsertParameter(
    command: UpsertLoginAccountParameterCommand
  ): LoginAccountParameterRecord =
    suspendTransaction(db = database) {
      val now = nowUtc()
      val existing =
        LoginAccountParametersTable.selectAll()
          .where {
            (LoginAccountParametersTable.loginAccountId eq
              command.loginAccountId.toKotlinUuid()) and
              (LoginAccountParametersTable.parameterKey eq command.parameterKey.value)
          }
          .singleOrNull()

      val id = existing?.get(LoginAccountParametersTable.id)?.toJavaUuid() ?: UUID.randomUUID()
      if (existing == null) {
        LoginAccountParametersTable.insert {
          it[LoginAccountParametersTable.id] = id.toKotlinUuid()
          it[LoginAccountParametersTable.loginAccountId] = command.loginAccountId.toKotlinUuid()
          it[LoginAccountParametersTable.parameterKey] = command.parameterKey.value
          it[LoginAccountParametersTable.parameterValue] = command.parameterValue
          it[LoginAccountParametersTable.secretRef] = command.secretRef
          it[LoginAccountParametersTable.metadata] = command.metadata
          it[LoginAccountParametersTable.createdAt] = now
          it[LoginAccountParametersTable.updatedAt] = now
        }
      } else {
        LoginAccountParametersTable.update({
          LoginAccountParametersTable.id eq id.toKotlinUuid()
        }) {
          it[LoginAccountParametersTable.parameterValue] = command.parameterValue
          it[LoginAccountParametersTable.secretRef] = command.secretRef
          it[LoginAccountParametersTable.metadata] = command.metadata
          it[LoginAccountParametersTable.updatedAt] = now
        }
      }

      LoginAccountParametersTable.selectAll()
        .where { LoginAccountParametersTable.id eq id.toKotlinUuid() }
        .single()
        .toLoginAccountParameterRecord()
    }

  override suspend fun findParameter(
    loginAccountId: UUID,
    parameterKey: LoginAccountParameterKey,
  ): LoginAccountParameterRecord? =
    suspendTransaction(db = database) {
      LoginAccountParametersTable.selectAll()
        .where {
          (LoginAccountParametersTable.loginAccountId eq loginAccountId.toKotlinUuid()) and
            (LoginAccountParametersTable.parameterKey eq parameterKey.value)
        }
        .singleOrNull()
        ?.toLoginAccountParameterRecord()
    }

  override suspend fun findLoginAccountByMethodAndSubject(
    loginMethodCode: String,
    normalizedSubject: String,
  ): LoginAccountRecord? =
    suspendTransaction(db = database) {
      val methodId = findLoginMethodId(loginMethodCode) ?: return@suspendTransaction null
      LoginAccountsTable.selectAll()
        .where {
          (LoginAccountsTable.disabledAt.isNull()) and
            (LoginAccountsTable.loginMethodId eq methodId) and
            (LoginAccountsTable.normalizedSubject eq normalizedSubject)
        }
        .singleOrNull()
        ?.toLoginAccountRecord()
    }

  override suspend fun findLoginAccountByParameterValue(
    loginMethodCode: String,
    parameterKey: LoginAccountParameterKey,
    parameterValue: String,
  ): LoginAccountRecord? =
    suspendTransaction(db = database) {
      val methodId = findLoginMethodId(loginMethodCode) ?: return@suspendTransaction null
      val parameter =
        LoginAccountParametersTable.selectAll()
          .where {
            (LoginAccountParametersTable.parameterKey eq parameterKey.value) and
              (LoginAccountParametersTable.parameterValue eq parameterValue)
          }
          .singleOrNull() ?: return@suspendTransaction null
      LoginAccountsTable.selectAll()
        .where {
          (LoginAccountsTable.disabledAt.isNull()) and
            (LoginAccountsTable.id eq parameter[LoginAccountParametersTable.loginAccountId]) and
            (LoginAccountsTable.loginMethodId eq methodId)
        }
        .singleOrNull()
        ?.toLoginAccountRecord()
    }

  override suspend fun touchLastUsed(loginAccountId: UUID, usedAt: OffsetDateTime): Boolean =
    suspendTransaction(db = database) {
      LoginAccountsTable.update({
        (LoginAccountsTable.id eq loginAccountId.toKotlinUuid()) and
          LoginAccountsTable.disabledAt.isNull()
      }) {
        it[LoginAccountsTable.lastUsedAt] = usedAt
        it[LoginAccountsTable.updatedAt] = usedAt
      } > 0
    }

  private fun findLoginMethodId(code: String) =
    LoginMethodDefinitionsTable.selectAll()
      .where {
        (LoginMethodDefinitionsTable.code eq code) and LoginMethodDefinitionsTable.isEnabledGlobally
      }
      .singleOrNull()
      ?.get(LoginMethodDefinitionsTable.id)
}
