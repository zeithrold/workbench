package doa.ink.workbench.core.identity

import doa.ink.workbench.core.identity.model.CreateTenantCommand
import doa.ink.workbench.core.identity.model.TenantRecord
import doa.ink.workbench.core.identity.model.UpdateTenantCommand
import java.util.UUID

interface TenantRepository {
  suspend fun create(command: CreateTenantCommand): TenantRecord

  suspend fun update(command: UpdateTenantCommand): TenantRecord

  suspend fun findById(id: UUID): TenantRecord?

  suspend fun findByApiId(apiId: String): TenantRecord?

  suspend fun findBySlug(slug: String): TenantRecord?

  suspend fun existsBySlug(slug: String): Boolean

  suspend fun findByIds(ids: Collection<UUID>): List<TenantRecord>

  suspend fun list(slug: String? = null): List<TenantRecord>
}
