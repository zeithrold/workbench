package ink.doa.workbench.tenant.tenantconfig

import ink.doa.workbench.tenant.tenantconfig.model.TenantConfigKey
import ink.doa.workbench.tenant.tenantconfig.model.TenantConfigRecord
import ink.doa.workbench.tenant.tenantconfig.model.UpsertTenantConfigCommand
import java.util.UUID

interface TenantConfigRepository {
  suspend fun findByTenantAndKey(tenantId: UUID, key: TenantConfigKey): TenantConfigRecord?

  suspend fun listByTenant(tenantId: UUID): List<TenantConfigRecord>

  suspend fun upsert(command: UpsertTenantConfigCommand): TenantConfigRecord
}
