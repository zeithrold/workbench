package doa.ink.workbench.core.identity

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
import java.time.OffsetDateTime
import java.util.UUID

interface UserRepository {
  suspend fun create(command: CreateUserCommand): UserRecord

  suspend fun findById(id: UUID): UserRecord?

  suspend fun findByApiId(apiId: String): UserRecord?

  suspend fun findByPrimaryEmail(primaryEmail: String): UserRecord?
}

interface TenantMemberRepository {
  suspend fun create(command: CreateTenantMemberCommand): TenantMemberRecord

  suspend fun findByTenantAndUser(tenantId: UUID, userId: UUID): TenantMemberRecord?

  suspend fun listByUser(userId: UUID): List<TenantMemberRecord>
}

@Suppress("TooManyFunctions")
interface LoginAccountRepository {
  suspend fun createLoginMethod(
    command: CreateLoginMethodDefinitionCommand
  ): LoginMethodDefinitionRecord

  suspend fun createTenantSetting(
    command: CreateTenantLoginMethodSettingCommand
  ): TenantLoginMethodSettingRecord

  suspend fun findTenantSetting(
    tenantId: UUID,
    loginMethodId: UUID,
  ): TenantLoginMethodSettingRecord?

  suspend fun createLoginAccount(command: CreateLoginAccountCommand): LoginAccountRecord

  suspend fun upsertParameter(
    command: UpsertLoginAccountParameterCommand
  ): LoginAccountParameterRecord

  suspend fun findParameter(
    loginAccountId: UUID,
    parameterKey: LoginAccountParameterKey,
  ): LoginAccountParameterRecord?

  suspend fun linkUser(command: LinkUserLoginAccountCommand): UserLoginAccountRecord

  suspend fun unlink(loginAccountId: UUID, unlinkedAt: OffsetDateTime): Boolean

  suspend fun findLinkedUser(loginAccountId: UUID): UserRecord?

  suspend fun findLoginAccountByMethodAndSubject(
    loginMethodCode: String,
    normalizedSubject: String,
  ): LoginAccountRecord?

  suspend fun findLoginMethodByCode(code: String): LoginMethodDefinitionRecord?

  suspend fun findLoginMethodByApiId(apiId: String): LoginMethodDefinitionRecord?

  suspend fun findLoginMethodById(id: UUID): LoginMethodDefinitionRecord?

  suspend fun findLoginAccountByParameterValue(
    loginMethodCode: String,
    parameterKey: LoginAccountParameterKey,
    parameterValue: String,
  ): LoginAccountRecord?

  suspend fun listLoginOptionsForIdentifier(normalizedIdentifier: String): List<TenantLoginOption>

  suspend fun findUserByMethodAndSubject(
    loginMethodCode: String,
    normalizedSubject: String,
  ): UserRecord?

  suspend fun touchLastUsed(loginAccountId: UUID, usedAt: OffsetDateTime): Boolean
}

interface AuthEventRepository {
  suspend fun append(command: CreateAuthEventCommand): AuthEventRecord

  suspend fun listRecentByUser(userId: UUID, limit: Int): List<AuthEventRecord>

  suspend fun listRecentByLoginAccount(loginAccountId: UUID, limit: Int): List<AuthEventRecord>
}
