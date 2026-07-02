package doa.ink.workbench.service.identity

import doa.ink.workbench.core.common.errors.InvalidRequestException
import doa.ink.workbench.core.common.errors.PermissionDeniedException
import doa.ink.workbench.core.common.errors.TenantNotSelectedException
import doa.ink.workbench.core.identity.TenantMemberRepository
import doa.ink.workbench.core.identity.TenantRepository
import doa.ink.workbench.core.identity.auth.AuthSessionRepository
import doa.ink.workbench.core.identity.model.AuthenticatedPrincipal
import doa.ink.workbench.core.identity.model.TenantMemberStatus
import doa.ink.workbench.core.identity.model.TenantRecord
import doa.ink.workbench.core.identity.model.UserRecord
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service

data class SessionView(
  val user: UserRecord,
  val activeTenant: TenantRecord?,
  val sessionExpiresAt: OffsetDateTime,
)

@Service
class SessionService(
  private val sessions: AuthSessionRepository,
  private val tenantMembers: TenantMemberRepository,
  private val tenants: TenantRepository,
  private val clock: Clock,
) {
  suspend fun getCurrent(principal: AuthenticatedPrincipal): SessionView {
    val sessionId = sessionUuid(principal)
    val session =
      sessions.findById(sessionId)
        ?: throw InvalidRequestException("Active session not found.")
    val activeTenant = session.activeTenantId?.let { tenants.findById(it) }
    return SessionView(
      user = principal.user,
      activeTenant = activeTenant,
      sessionExpiresAt = session.expiresAt,
    )
  }

  suspend fun switchTenant(principal: AuthenticatedPrincipal, tenantApiId: String?): SessionView {
    val sessionId = sessionUuid(principal)
    val now = OffsetDateTime.now(clock)
    val tenantId =
      if (tenantApiId == null) {
        null
      } else {
        val tenant =
          tenants.findByApiId(tenantApiId)
            ?: throw InvalidRequestException("Unknown tenant: $tenantApiId")
        val membership = tenantMembers.findByTenantAndUser(tenant.id, principal.user.id)
        if (membership?.status != TenantMemberStatus.ACTIVE) {
          throw PermissionDeniedException("You are not an active member of this tenant.")
        }
        tenant.id
      }
    val updated = sessions.updateActiveTenant(sessionId, tenantId, now)
    if (!updated) {
      throw InvalidRequestException("Unable to update session tenant.")
    }
    return getCurrent(principal)
  }

  suspend fun requireActiveTenantId(principal: AuthenticatedPrincipal): UUID {
    val sessionId = sessionUuid(principal)
    val session =
      sessions.findById(sessionId)
        ?: throw InvalidRequestException("Active session not found.")
    val tenantId =
      session.activeTenantId
        ?: throw TenantNotSelectedException("Select a tenant via PATCH /api/session before calling tenant-scoped APIs.")
    val membership = tenantMembers.findByTenantAndUser(tenantId, principal.user.id)
    if (membership?.status != TenantMemberStatus.ACTIVE) {
      throw PermissionDeniedException("You are not an active member of the selected tenant.")
    }
    return tenantId
  }

  suspend fun requireActiveTenant(principal: AuthenticatedPrincipal): TenantRecord {
    val tenantId = requireActiveTenantId(principal)
    return tenants.findById(tenantId)
      ?: throw InvalidRequestException("Selected tenant no longer exists.")
  }

  private fun sessionUuid(principal: AuthenticatedPrincipal): UUID {
    val sessionId =
      principal.sessionId ?: throw InvalidRequestException("Session context is required for tenant switching.")
    return UUID.fromString(sessionId)
  }
}
