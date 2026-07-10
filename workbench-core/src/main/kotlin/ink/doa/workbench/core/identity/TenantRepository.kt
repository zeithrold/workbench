package ink.doa.workbench.core.identity

import ink.doa.workbench.core.identity.model.CreateTenantCommand
import ink.doa.workbench.core.identity.model.FinalizeTenantDestroyCommand
import ink.doa.workbench.core.identity.model.TenantRecord
import ink.doa.workbench.core.identity.model.UpdateTenantCommand
import ink.doa.workbench.core.tenant.events.TenantDestroyRequestedEvent
import java.util.UUID

@Suppress("TooManyFunctions")
interface TenantRepository {
  suspend fun create(command: CreateTenantCommand): TenantRecord

  suspend fun update(command: UpdateTenantCommand): TenantRecord

  suspend fun markDestroying(tenantId: UUID): TenantRecord

  suspend fun requestDestroy(
    tenantId: UUID,
    tenantApiId: String,
    payload: TenantDestroyRequestedEvent,
  ): TenantRecord

  suspend fun finalizeDestroy(command: FinalizeTenantDestroyCommand): Boolean

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
