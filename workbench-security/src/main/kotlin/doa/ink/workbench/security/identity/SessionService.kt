@file:Suppress("ThrowsCount")

package doa.ink.workbench.security.identity

import doa.ink.workbench.core.common.errors.InvalidRequestException
import doa.ink.workbench.core.common.errors.PermissionDeniedException
import doa.ink.workbench.core.common.errors.ResourceNotFoundException
import doa.ink.workbench.core.common.errors.TenantDestroyingException
import doa.ink.workbench.core.common.summary.TenantSummary
import doa.ink.workbench.core.common.summary.UserSummary
import doa.ink.workbench.core.identity.TenantMemberRepository
import doa.ink.workbench.core.identity.TenantRepository
import doa.ink.workbench.core.identity.auth.AuthSessionRepository
import doa.ink.workbench.core.identity.model.AuthenticatedPrincipal
import doa.ink.workbench.core.identity.model.TenantMemberStatus
import doa.ink.workbench.core.identity.model.TenantRecord
import doa.ink.workbench.core.identity.model.TenantStatus
import doa.ink.workbench.security.common.PublicIdResolver
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class SessionService(
  private val sessions: AuthSessionRepository,
  private val tenantMembers: TenantMemberRepository,
  private val tenants: TenantRepository,
  private val publicIds: PublicIdResolver,
  private val loginCompletionService: LoginCompletionService,
  private val clock: Clock,
) {
  suspend fun getCurrent(principal: AuthenticatedPrincipal): SessionView {
    val sessionId = sessionUuid(principal)
    val session =
      sessions.findById(sessionId) ?: throw InvalidRequestException("Active session not found.")
    val activeTenant = session.activeTenantId?.let { tenants.findById(it) }
    return SessionView(
      user = UserSummary.from(principal.user),
      activeTenant = activeTenant?.let { TenantSummary.from(it) },
      sessionExpiresAt = session.expiresAt,
      adminScopes = loginCompletionService.adminScopes(principal.user.id),
    )
  }

  suspend fun tenantSummary(tenantId: UUID): TenantSummary? =
    tenants.findById(tenantId)?.let { TenantSummary.from(it) }

  suspend fun switchTenant(principal: AuthenticatedPrincipal, tenantId: String?): SessionView {
    val sessionId = sessionUuid(principal)
    val now = OffsetDateTime.now(clock)
    val resolvedTenantId =
      if (tenantId == null) {
        null
      } else {
        val tenant = publicIds.resolveTenant(tenantId)
        ensureTenantOperational(tenant.id)
        val membership = tenantMembers.findByTenantAndUser(tenant.id, principal.user.id)
        if (membership?.status != TenantMemberStatus.ACTIVE) {
          throw PermissionDeniedException("You are not an active member of this tenant.")
        }
        tenant.id
      }
    val updated = sessions.updateActiveTenant(sessionId, resolvedTenantId, now)
    if (!updated) {
      throw InvalidRequestException("Unable to update session tenant.")
    }
    return getCurrent(principal)
  }

  suspend fun requireActiveTenantId(principal: AuthenticatedPrincipal): UUID {
    principal.tenantId?.let { activeTenantId ->
      val membership = tenantMembers.findByTenantAndUser(activeTenantId, principal.user.id)
      if (membership?.status != TenantMemberStatus.ACTIVE) {
        throw PermissionDeniedException("You are not an active member of the selected tenant.")
      }
      ensureTenantOperational(activeTenantId)
      return activeTenantId
    }
    val sessionId = sessionUuid(principal)
    val session =
      sessions.findById(sessionId) ?: throw InvalidRequestException("Active session not found.")
    val activeTenantId =
      session.activeTenantId
        ?: throw doa.ink.workbench.core.common.errors.TenantNotSelectedException(
          "Select a tenant via PATCH /api/session before calling tenant-scoped APIs."
        )
    val membership = tenantMembers.findByTenantAndUser(activeTenantId, principal.user.id)
    if (membership?.status != TenantMemberStatus.ACTIVE) {
      throw PermissionDeniedException("You are not an active member of the selected tenant.")
    }
    ensureTenantOperational(activeTenantId)
    return activeTenantId
  }

  suspend fun requireActiveTenant(principal: AuthenticatedPrincipal): TenantRecord {
    val tenantId = requireActiveTenantId(principal)
    val tenant =
      tenants.findById(tenantId)
        ?: throw InvalidRequestException("Selected tenant no longer exists.")
    ensureTenantOperational(tenant.id)
    return tenant
  }

  private suspend fun ensureTenantOperational(tenantId: UUID) {
    val tenant = tenants.findById(tenantId) ?: throw ResourceNotFoundException("Tenant not found.")
    if (tenant.status == TenantStatus.DESTROYING) {
      throw TenantDestroyingException("Tenant is being destroyed.")
    }
  }

  private fun sessionUuid(principal: AuthenticatedPrincipal): UUID {
    val sessionId =
      principal.sessionId
        ?: throw InvalidRequestException("Session context is required for tenant switching.")
    return UUID.fromString(sessionId)
  }
}
