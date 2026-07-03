package doa.ink.workbench.core.identity

import doa.ink.workbench.core.identity.model.CreateTenantCommand
import doa.ink.workbench.core.identity.model.FinalizeTenantDestroyCommand
import doa.ink.workbench.core.identity.model.TenantRecord
import doa.ink.workbench.core.identity.model.UpdateTenantCommand
import java.util.UUID

@Suppress("TooManyFunctions")
interface TenantRepository {
  suspend fun create(command: CreateTenantCommand): TenantRecord

  suspend fun update(command: UpdateTenantCommand): TenantRecord

  suspend fun markDestroying(tenantId: UUID): TenantRecord

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
