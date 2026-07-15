package ink.doa.workbench.identity

import ink.doa.workbench.identity.auth.AuthSessionRepository
import ink.doa.workbench.identity.common.IdentityPublicIdResolver
import ink.doa.workbench.identity.common.summary.UserSummary
import ink.doa.workbench.identity.model.AuthenticatedPrincipal
import ink.doa.workbench.identity.model.TenantMemberStatus
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.PermissionDeniedException
import ink.doa.workbench.kernel.common.errors.ResourceNotFoundException
import ink.doa.workbench.kernel.common.errors.TenantDestroyingException
import ink.doa.workbench.kernel.common.errors.TenantNotSelectedException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import ink.doa.workbench.tenant.TenantRepository
import ink.doa.workbench.tenant.common.summary.TenantSummary
import ink.doa.workbench.tenant.model.TenantRecord
import ink.doa.workbench.tenant.model.TenantStatus
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class SessionService(
  private val sessions: AuthSessionRepository,
  private val tenantMembers: TenantMemberRepository,
  private val tenants: TenantRepository,
  private val publicIds: IdentityPublicIdResolver,
  private val loginCompletionService: LoginCompletionService,
  private val clock: Clock,
) {
  suspend fun getCurrent(principal: AuthenticatedPrincipal): SessionView {
    val sessionId = sessionUuid(principal)
    val session =
      sessions.findById(sessionId)
        ?: throw InvalidRequestException(WorkbenchErrorCode.SESSION_ACTIVE_NOT_FOUND)
    val activeTenant = session.activeTenantId?.let { tenants.findById(it) }
    return SessionView(
      user = UserSummary.from(principal.user),
      activeTenant = activeTenant?.let { TenantSummary.from(it) },
      sessionExpiresAt = session.expiresAt,
      adminScopes = loginCompletionService.adminScopes(principal.user.id),
      localeContext =
        LocaleContextView(
          userPreference = principal.user.locale,
          tenantDefault = activeTenant?.locale,
        ),
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
          throw PermissionDeniedException(WorkbenchErrorCode.AUTH_TENANT_MEMBERSHIP_REQUIRED)
        }
        tenant.id
      }
    val updated = sessions.updateActiveTenant(sessionId, resolvedTenantId, now)
    if (!updated) {
      throw InvalidRequestException(WorkbenchErrorCode.SESSION_TENANT_UPDATE_FAILED)
    }
    return getCurrent(principal)
  }

  suspend fun requireActiveTenantId(principal: AuthenticatedPrincipal): UUID {
    principal.tenantId?.let { activeTenantId ->
      ensureActiveMembership(activeTenantId, principal.user.id)
      return activeTenantId
    }
    val sessionId = sessionUuid(principal)
    val session =
      sessions.findById(sessionId)
        ?: throw InvalidRequestException(WorkbenchErrorCode.SESSION_ACTIVE_NOT_FOUND)
    val activeTenantId = session.activeTenantId ?: throw TenantNotSelectedException()
    ensureActiveMembership(activeTenantId, principal.user.id)
    return activeTenantId
  }

  private suspend fun ensureActiveMembership(tenantId: UUID, userId: UUID) {
    val membership = tenantMembers.findByTenantAndUser(tenantId, userId)
    if (membership?.status != TenantMemberStatus.ACTIVE) {
      throw PermissionDeniedException(WorkbenchErrorCode.AUTH_SELECTED_TENANT_MEMBERSHIP_REQUIRED)
    }
    ensureTenantOperational(tenantId)
  }

  suspend fun requireActiveTenant(principal: AuthenticatedPrincipal): TenantRecord {
    val tenantId = requireActiveTenantId(principal)
    val tenant =
      tenants.findById(tenantId)
        ?: throw InvalidRequestException(WorkbenchErrorCode.SESSION_SELECTED_TENANT_MISSING)
    ensureTenantOperational(tenant.id)
    return tenant
  }

  private suspend fun ensureTenantOperational(tenantId: UUID) {
    val tenant =
      tenants.findById(tenantId)
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_TENANT_NOT_FOUND)
    if (tenant.status == TenantStatus.DESTROYING) {
      throw TenantDestroyingException()
    }
  }

  private fun sessionUuid(principal: AuthenticatedPrincipal): UUID {
    val sessionId =
      principal.sessionId
        ?: throw InvalidRequestException(WorkbenchErrorCode.SESSION_CONTEXT_REQUIRED)
    return UUID.fromString(sessionId)
  }
}
