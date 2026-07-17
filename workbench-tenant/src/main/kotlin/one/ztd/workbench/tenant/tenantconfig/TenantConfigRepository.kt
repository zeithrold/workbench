package one.ztd.workbench.tenant.tenantconfig

import java.util.UUID
import one.ztd.workbench.tenant.tenantconfig.model.TenantConfigKey
import one.ztd.workbench.tenant.tenantconfig.model.TenantConfigRecord
import one.ztd.workbench.tenant.tenantconfig.model.UpsertTenantConfigCommand

interface TenantConfigRepository {
  suspend fun findByTenantAndKey(tenantId: UUID, key: TenantConfigKey): TenantConfigRecord?

  suspend fun listByTenant(tenantId: UUID): List<TenantConfigRecord>

  suspend fun upsert(command: UpsertTenantConfigCommand): TenantConfigRecord
}
