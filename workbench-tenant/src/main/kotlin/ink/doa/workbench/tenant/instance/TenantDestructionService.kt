package ink.doa.workbench.tenant.instance

import ink.doa.workbench.core.identity.TenantRepository
import ink.doa.workbench.core.identity.model.FinalizeTenantDestroyCommand
import ink.doa.workbench.core.identity.model.TenantStatus
import ink.doa.workbench.core.tenant.TenantDestructionRepository
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class TenantDestructionService(
  private val tenants: TenantRepository,
  private val destruction: TenantDestructionRepository,
  private val clock: Clock,
) {
  private val logger = LoggerFactory.getLogger(javaClass)

  @Suppress("ReturnCount")
  suspend fun execute(tenantId: UUID, deletedBy: UUID, deleteReason: String?): Boolean {
    val tenant =
      tenants.findByIdForDestruction(tenantId)
        ?: run {
          logger.warn("tenant_destroy_skipped tenantId={} reason=not_found", tenantId)
          return false
        }
    if (tenant.status != TenantStatus.DESTROYING) {
      logger.warn(
        "tenant_destroy_skipped tenantId={} reason=invalid_status status={}",
        tenant.apiId.value,
        tenant.status,
      )
      return false
    }

    val now = OffsetDateTime.now(clock)
    logger.info("tenant_destroy_started tenantId={}", tenant.apiId.value)

    destruction.revokeSessionsByActiveTenant(tenantId, now)
    destruction.revokeBearerTokensByTenant(tenantId, now)
    destruction.revokeAdminUsersByTenant(tenantId, now)
    destruction.expireAccessGrantsByTenant(tenantId, now)
    destruction.cancelPendingInvitationsByTenant(tenantId, now)
    destruction.softDeleteTenantScopedData(tenantId, now, deletedBy, deleteReason)

    val finalized =
      tenants.finalizeDestroy(
        FinalizeTenantDestroyCommand(
          tenantId = tenantId,
          deletedBy = deletedBy,
          deleteReason = deleteReason,
        )
      )
    if (finalized) {
      logger.info("tenant_destroy_completed tenantId={}", tenant.apiId.value)
      return true
    }
    logger.warn(
      "tenant_destroy_finalize_skipped tenantId={} reason=already_deleted",
      tenant.apiId.value,
    )
    return false
  }
}
