package ink.doa.workbench.core.permission

import java.time.OffsetDateTime
import java.util.UUID

interface AdminUserCommandRepository {
  suspend fun create(command: CreateAdminUserCommand): AdminUserRecord

  suspend fun revoke(id: UUID, revokedAt: OffsetDateTime): Boolean

  suspend fun revokeByTenant(tenantId: UUID, revokedAt: OffsetDateTime): Int
}

interface AdminUserQueryRepository {
  suspend fun findById(id: UUID): AdminUserRecord?

  suspend fun findByApiId(apiId: String): AdminUserRecord?

  suspend fun findActiveInstanceAdmin(userId: UUID, at: OffsetDateTime): AdminUserRecord?

  suspend fun findActiveTenantAdmin(
    tenantId: UUID,
    userId: UUID,
    at: OffsetDateTime,
  ): AdminUserRecord?

  suspend fun existsActiveInstanceAdmin(): Boolean

  suspend fun isActiveInstanceAdmin(userId: UUID, at: OffsetDateTime): Boolean

  suspend fun isActiveTenantAdmin(tenantId: UUID, userId: UUID, at: OffsetDateTime): Boolean

  suspend fun listByUser(userId: UUID): List<AdminUserRecord>

  suspend fun listInstanceAdmins(): List<AdminUserRecord>

  suspend fun listTenantAdmins(tenantId: UUID): List<AdminUserRecord>
}
