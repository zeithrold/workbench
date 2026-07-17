package one.ztd.workbench.identity

import java.time.OffsetDateTime
import one.ztd.workbench.identity.model.CreateInvitationCommand
import one.ztd.workbench.identity.model.InvitationRecord

interface InvitationRepository {
  suspend fun create(command: CreateInvitationCommand): InvitationRecord

  suspend fun findActiveByHash(tokenHash: String, now: OffsetDateTime): InvitationRecord?

  suspend fun listPendingByTenant(
    tenantId: java.util.UUID,
    now: OffsetDateTime,
  ): List<InvitationRecord>

  suspend fun cancelPending(
    tenantId: java.util.UUID,
    apiId: String,
    cancelledAt: OffsetDateTime,
  ): Boolean

  suspend fun consume(id: java.util.UUID, consumedAt: OffsetDateTime): Boolean

  suspend fun cancelPendingByTenant(tenantId: java.util.UUID, cancelledAt: OffsetDateTime): Int
}
