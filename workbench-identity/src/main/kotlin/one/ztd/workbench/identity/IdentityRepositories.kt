package one.ztd.workbench.identity

import java.time.OffsetDateTime
import java.util.UUID
import one.ztd.workbench.identity.model.AuthEventRecord
import one.ztd.workbench.identity.model.CreateAuthEventCommand
import one.ztd.workbench.identity.model.CreateTenantMemberCommand
import one.ztd.workbench.identity.model.CreateUserCommand
import one.ztd.workbench.identity.model.TenantMemberRecord
import one.ztd.workbench.identity.model.UserRecord

interface UserRepository {
  suspend fun create(command: CreateUserCommand): UserRecord

  suspend fun findById(id: UUID): UserRecord?

  suspend fun findByApiId(apiId: String): UserRecord?

  suspend fun findByPrimaryEmail(primaryEmail: String): UserRecord?
}

interface UserPreferenceRepository {
  suspend fun updateLocale(userId: UUID, locale: String?, updatedAt: OffsetDateTime): UserRecord?
}

interface TenantMemberRepository {
  suspend fun create(command: CreateTenantMemberCommand): TenantMemberRecord

  suspend fun findByTenantAndUser(tenantId: UUID, userId: UUID): TenantMemberRecord?

  suspend fun findByApiId(tenantId: UUID, apiId: String): TenantMemberRecord?

  suspend fun listByTenant(tenantId: UUID): List<TenantMemberRecord>

  suspend fun listByUser(userId: UUID): List<TenantMemberRecord>

  suspend fun updateStatus(
    id: UUID,
    status: one.ztd.workbench.identity.model.TenantMemberStatus,
    updatedAt: OffsetDateTime,
  ): TenantMemberRecord?
}

interface AuthEventRepository {
  suspend fun append(command: CreateAuthEventCommand): AuthEventRecord

  suspend fun listRecentByUser(userId: UUID, limit: Int): List<AuthEventRecord>

  suspend fun listRecentByLoginAccount(loginAccountId: UUID, limit: Int): List<AuthEventRecord>
}
