package one.ztd.workbench.tenant

import java.util.UUID
import one.ztd.workbench.tenant.model.CreateTenantCommand
import one.ztd.workbench.tenant.model.FinalizeTenantDestroyCommand
import one.ztd.workbench.tenant.model.TenantRecord
import one.ztd.workbench.tenant.model.UpdateTenantCommand
import one.ztd.workbench.tenant.tenant.events.TenantDestroyRequestedEvent

interface TenantRepository : TenantMutationRepository, TenantQueryRepository

interface TenantMutationRepository {
  suspend fun create(command: CreateTenantCommand): TenantRecord

  suspend fun update(command: UpdateTenantCommand): TenantRecord

  suspend fun markDestroying(tenantId: UUID): TenantRecord

  suspend fun requestDestroy(
    tenantId: UUID,
    tenantApiId: String,
    payload: TenantDestroyRequestedEvent,
  ): TenantRecord

  suspend fun finalizeDestroy(command: FinalizeTenantDestroyCommand): Boolean
}

interface TenantQueryRepository {
  suspend fun findById(id: UUID): TenantRecord?

  suspend fun findByIdForDestruction(id: UUID): TenantRecord?

  suspend fun findByApiId(apiId: String): TenantRecord?

  suspend fun findByApiIdForAdmin(apiId: String): TenantRecord?

  suspend fun findBySlug(slug: String): TenantRecord?

  suspend fun existsBySlug(slug: String): Boolean

  suspend fun findByIds(ids: Collection<UUID>): List<TenantRecord>

  suspend fun list(slug: String? = null): List<TenantRecord>

  suspend fun listForAdmin(slug: String? = null): List<TenantRecord>
}
