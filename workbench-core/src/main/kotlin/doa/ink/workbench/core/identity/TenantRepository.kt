package doa.ink.workbench.core.identity

import doa.ink.workbench.core.identity.model.TenantRecord
import java.util.UUID

interface TenantRepository {
  suspend fun findById(id: UUID): TenantRecord?

  suspend fun findByApiId(apiId: String): TenantRecord?

  suspend fun findByIds(ids: Collection<UUID>): List<TenantRecord>
}
