package ink.doa.workbench.identity

import ink.doa.workbench.identity.model.CreateTenantLoginMethodSettingCommand
import ink.doa.workbench.identity.model.TenantLoginMethodSettingRecord
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
