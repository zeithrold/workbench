package ink.doa.workbench.application.invitation

import ink.doa.workbench.identity.InvitationRepository
import ink.doa.workbench.kernel.common.errors.ResourceNotFoundException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class ManagedInvitationService(
  private val invitations: InvitationRepository,
  private val clock: Clock,
) {
  suspend fun listPending(tenantId: UUID): List<ManagedInvitationView> =
    invitations.listPendingByTenant(tenantId, OffsetDateTime.now(clock)).map {
      ManagedInvitationView(
        id = it.apiId.value,
        type = it.type,
        email = it.email,
        displayName = it.displayName,
        expiresAt = it.expiresAt,
        createdAt = it.createdAt,
      )
    }

  suspend fun cancel(tenantId: UUID, invitationId: String) {
    if (!invitations.cancelPending(tenantId, invitationId, OffsetDateTime.now(clock))) {
      throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_INVITATION_NOT_FOUND)
    }
  }
}
