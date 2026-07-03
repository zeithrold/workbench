package doa.ink.workbench.core.identity

import doa.ink.workbench.core.identity.model.CreateInvitationCommand
import doa.ink.workbench.core.identity.model.InvitationRecord
import java.time.OffsetDateTime

interface InvitationRepository {
  suspend fun create(command: CreateInvitationCommand): InvitationRecord

  suspend fun findActiveByHash(tokenHash: String, now: OffsetDateTime): InvitationRecord?

  suspend fun consume(id: java.util.UUID, consumedAt: OffsetDateTime): Boolean
}
