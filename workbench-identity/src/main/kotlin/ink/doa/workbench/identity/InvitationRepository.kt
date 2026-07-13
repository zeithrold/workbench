package ink.doa.workbench.identity

import ink.doa.workbench.identity.model.CreateInvitationCommand
import ink.doa.workbench.identity.model.InvitationRecord
import java.time.OffsetDateTime

interface InvitationRepository {
  suspend fun create(command: CreateInvitationCommand): InvitationRecord

  suspend fun findActiveByHash(tokenHash: String, now: OffsetDateTime): InvitationRecord?

  suspend fun consume(id: java.util.UUID, consumedAt: OffsetDateTime): Boolean

  suspend fun cancelPendingByTenant(tenantId: java.util.UUID, cancelledAt: OffsetDateTime): Int
}
