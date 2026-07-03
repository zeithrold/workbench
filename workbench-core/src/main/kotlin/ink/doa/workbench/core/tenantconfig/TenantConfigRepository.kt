package ink.doa.workbench.core.tenantconfig

import ink.doa.workbench.core.tenantconfig.model.TenantConfigKey
import ink.doa.workbench.core.tenantconfig.model.TenantConfigRecord
import ink.doa.workbench.core.tenantconfig.model.UpsertTenantConfigCommand
import java.util.UUID

interface TenantConfigRepository {
  suspend fun findByTenantAndKey(tenantId: UUID, key: TenantConfigKey): TenantConfigRecord?

  suspend fun listByTenant(tenantId: UUID): List<TenantConfigRecord>

  suspend fun upsert(command: UpsertTenantConfigCommand): TenantConfigRecord
}
