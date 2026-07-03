package doa.ink.workbench.core.tenant

import java.time.OffsetDateTime
import java.util.UUID

interface TenantDestructionRepository {
  suspend fun revokeSessionsByActiveTenant(tenantId: UUID, revokedAt: OffsetDateTime): Int

  suspend fun revokeBearerTokensByTenant(tenantId: UUID, revokedAt: OffsetDateTime): Int

  suspend fun revokeAdminUsersByTenant(tenantId: UUID, revokedAt: OffsetDateTime): Int

  suspend fun expireAccessGrantsByTenant(tenantId: UUID, expiredAt: OffsetDateTime): Int

  suspend fun cancelPendingInvitationsByTenant(tenantId: UUID, cancelledAt: OffsetDateTime): Int

  suspend fun softDeleteTenantScopedData(
    tenantId: UUID,
    deletedAt: OffsetDateTime,
    deletedBy: UUID,
    deleteReason: String?,
  ): Unit
}
