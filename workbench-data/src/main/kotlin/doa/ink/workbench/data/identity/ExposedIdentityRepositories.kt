package doa.ink.workbench.data.identity

import doa.ink.workbench.core.common.ids.PublicId
import doa.ink.workbench.core.common.summary.LoginMethodSummary
import doa.ink.workbench.core.common.summary.TenantSummary
import doa.ink.workbench.core.identity.AuthEventRepository
import doa.ink.workbench.core.identity.LoginAccountRepository
import doa.ink.workbench.core.identity.TenantMemberRepository
import doa.ink.workbench.core.identity.UserRepository
import doa.ink.workbench.core.identity.model.AuthEventRecord
import doa.ink.workbench.core.identity.model.CreateAuthEventCommand
import doa.ink.workbench.core.identity.model.CreateLoginAccountCommand
import doa.ink.workbench.core.identity.model.CreateLoginMethodDefinitionCommand
import doa.ink.workbench.core.identity.model.CreateTenantLoginMethodSettingCommand
import doa.ink.workbench.core.identity.model.CreateTenantMemberCommand
import doa.ink.workbench.core.identity.model.CreateUserCommand
import doa.ink.workbench.core.identity.model.LinkUserLoginAccountCommand
import doa.ink.workbench.core.identity.model.LoginAccountParameterKey
import doa.ink.workbench.core.identity.model.LoginAccountParameterRecord
import doa.ink.workbench.core.identity.model.LoginAccountRecord
import doa.ink.workbench.core.identity.model.LoginMethodDefinitionRecord
import doa.ink.workbench.core.identity.model.TenantLoginMethodSettingRecord
import doa.ink.workbench.core.identity.model.TenantLoginOption
import doa.ink.workbench.core.identity.model.TenantMemberRecord
import doa.ink.workbench.core.identity.model.UpsertLoginAccountParameterCommand
import doa.ink.workbench.core.identity.model.UserLoginAccountRecord
import doa.ink.workbench.core.identity.model.UserRecord
import doa.ink.workbench.data.persistence.AuthEventsTable
import doa.ink.workbench.data.persistence.LoginAccountParametersTable
import doa.ink.workbench.data.persistence.LoginAccountsTable
import doa.ink.workbench.data.persistence.LoginMethodDefinitionsTable
import doa.ink.workbench.data.persistence.TenantLoginMethodSettingsTable
import doa.ink.workbench.data.persistence.TenantMembersTable
import doa.ink.workbench.data.persistence.TenantsTable
import doa.ink.workbench.data.persistence.UserLoginAccountsTable
import doa.ink.workbench.data.persistence.UsersTable
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.SortOrder
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
        it[UsersTable.isSystem] = command.isSystem
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

  override suspend fun existsSystemUser(): Boolean =
    suspendTransaction(db = database) {
      UsersTable.selectAll()
        .where { (UsersTable.deletedAt.isNull()) and (UsersTable.isSystem eq true) }
        .limit(1)
        .any()
    }
}

@Repository
class ExposedTenantMemberRepository(private val database: Database) : TenantMemberRepository {
  override suspend fun create(command: CreateTenantMemberCommand): TenantMemberRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val now = nowUtc()
      TenantMembersTable.insert {
        it[TenantMembersTable.id] = id.toKotlinUuid()
        it[TenantMembersTable.apiId] = PublicId.new("tmb").value
        it[TenantMembersTable.tenantId] = command.tenantId.toKotlinUuid()
        it[TenantMembersTable.userId] = command.userId.toKotlinUuid()
        it[TenantMembersTable.status] = command.status.dbValue
        it[TenantMembersTable.joinedAt] = command.joinedAt
        it[TenantMembersTable.invitedBy] = command.invitedBy?.toKotlinUuid()
        it[TenantMembersTable.createdAt] = now
        it[TenantMembersTable.updatedAt] = now
      }
      TenantMembersTable.selectAll()
        .where {
          (TenantMembersTable.deletedAt.isNull()) and (TenantMembersTable.id eq id.toKotlinUuid())
        }
        .single()
        .toTenantMemberRecord()
    }

  override suspend fun findByTenantAndUser(tenantId: UUID, userId: UUID): TenantMemberRecord? =
    suspendTransaction(db = database) {
      TenantMembersTable.selectAll()
        .where {
          (TenantMembersTable.deletedAt.isNull()) and
            (TenantMembersTable.tenantId eq tenantId.toKotlinUuid()) and
            (TenantMembersTable.userId eq userId.toKotlinUuid())
        }
        .singleOrNull()
        ?.toTenantMemberRecord()
    }

  override suspend fun listByUser(userId: UUID): List<TenantMemberRecord> =
    suspendTransaction(db = database) {
      TenantMembersTable.selectAll()
        .where {
          (TenantMembersTable.deletedAt.isNull()) and
            (TenantMembersTable.userId eq userId.toKotlinUuid())
        }
        .map { it.toTenantMemberRecord() }
    }
}

@Repository
@Suppress("TooManyFunctions")
class ExposedLoginAccountRepository(private val database: Database) : LoginAccountRepository {
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

  override suspend fun createTenantSetting(
    command: CreateTenantLoginMethodSettingCommand
  ): TenantLoginMethodSettingRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val now = nowUtc()
      TenantLoginMethodSettingsTable.insert {
        it[TenantLoginMethodSettingsTable.id] = id.toKotlinUuid()
        it[TenantLoginMethodSettingsTable.tenantId] = command.tenantId.toKotlinUuid()
        it[TenantLoginMethodSettingsTable.loginMethodId] = command.loginMethodId.toKotlinUuid()
        it[TenantLoginMethodSettingsTable.isEnabled] = command.isEnabled
        it[TenantLoginMethodSettingsTable.allowSignup] = command.allowSignup
        it[TenantLoginMethodSettingsTable.displayOrder] = command.displayOrder
        it[TenantLoginMethodSettingsTable.config] = command.config
        it[TenantLoginMethodSettingsTable.secretRef] = command.secretRef
        it[TenantLoginMethodSettingsTable.createdBy] = command.createdBy?.toKotlinUuid()
        it[TenantLoginMethodSettingsTable.updatedBy] = command.updatedBy?.toKotlinUuid()
        it[TenantLoginMethodSettingsTable.createdAt] = now
        it[TenantLoginMethodSettingsTable.updatedAt] = now
      }
      TenantLoginMethodSettingsTable.selectAll()
        .where { TenantLoginMethodSettingsTable.id eq id.toKotlinUuid() }
        .single()
        .toTenantLoginMethodSettingRecord()
    }

  override suspend fun findTenantSetting(
    tenantId: UUID,
    loginMethodId: UUID,
  ): TenantLoginMethodSettingRecord? =
    suspendTransaction(db = database) {
      TenantLoginMethodSettingsTable.selectAll()
        .where {
          (TenantLoginMethodSettingsTable.tenantId eq tenantId.toKotlinUuid()) and
            (TenantLoginMethodSettingsTable.loginMethodId eq loginMethodId.toKotlinUuid())
        }
        .singleOrNull()
        ?.toTenantLoginMethodSettingRecord()
    }

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

  override suspend fun listLoginOptionsForIdentifier(
    normalizedIdentifier: String
  ): List<TenantLoginOption> =
    suspendTransaction(db = database) {
      val user =
        UsersTable.selectAll()
          .where {
            (UsersTable.deletedAt.isNull()) and (UsersTable.primaryEmail eq normalizedIdentifier)
          }
          .singleOrNull()
          ?.toUserRecord() ?: findUserByMethodAndSubject("password", normalizedIdentifier)

      if (user == null) {
        return@suspendTransaction emptyList()
      }

      val memberships =
        TenantMembersTable.selectAll()
          .where {
            (TenantMembersTable.userId eq user.id.toKotlinUuid()) and
              (TenantMembersTable.status eq "active") and
              TenantMembersTable.deletedAt.isNull()
          }
          .map { it.toTenantMemberRecord() }

      memberships.flatMap { membership ->
        val tenant =
          TenantsTable.selectAll()
            .where {
              (TenantsTable.id eq membership.tenantId.toKotlinUuid()) and
                TenantsTable.deletedAt.isNull()
            }
            .singleOrNull()
            ?.toTenantRecord() ?: return@flatMap emptyList()

        TenantLoginMethodSettingsTable.selectAll()
          .where {
            (TenantLoginMethodSettingsTable.tenantId eq membership.tenantId.toKotlinUuid()) and
              TenantLoginMethodSettingsTable.isEnabled
          }
          .mapNotNull { settingRow ->
            val methodRow =
              LoginMethodDefinitionsTable.selectAll()
                .where {
                  (LoginMethodDefinitionsTable.id eq
                    settingRow[TenantLoginMethodSettingsTable.loginMethodId]) and
                    LoginMethodDefinitionsTable.isEnabledGlobally
                }
                .singleOrNull() ?: return@mapNotNull null
            val method = methodRow.toLoginMethodDefinitionRecord()
            TenantLoginOption(
              tenant = TenantSummary.from(tenant),
              loginMethod = LoginMethodSummary.from(method),
            )
          }
      }
    }

  override suspend fun findUserByMethodAndSubject(
    loginMethodCode: String,
    normalizedSubject: String,
  ): UserRecord? =
    suspendTransaction(db = database) {
      val loginAccount =
        findLoginAccountByMethodAndSubject(loginMethodCode, normalizedSubject)
          ?: return@suspendTransaction null
      val link =
        UserLoginAccountsTable.selectAll()
          .where {
            (UserLoginAccountsTable.loginAccountId eq loginAccount.id.toKotlinUuid()) and
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

private fun nowUtc(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)

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
