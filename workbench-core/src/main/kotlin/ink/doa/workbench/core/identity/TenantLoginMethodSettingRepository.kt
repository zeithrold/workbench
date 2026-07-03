package ink.doa.workbench.core.identity

import ink.doa.workbench.core.identity.model.CreateTenantLoginMethodSettingCommand
import ink.doa.workbench.core.identity.model.TenantLoginMethodSettingRecord
import java.util.UUID

interface TenantLoginMethodSettingRepository {
  suspend fun createTenantSetting(
    command: CreateTenantLoginMethodSettingCommand
  ): TenantLoginMethodSettingRecord

  suspend fun findTenantSetting(
    tenantId: UUID,
    loginMethodId: UUID,
  ): TenantLoginMethodSettingRecord?
}
