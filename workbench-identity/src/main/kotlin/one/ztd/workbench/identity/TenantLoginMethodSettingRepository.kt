package one.ztd.workbench.identity

import java.util.UUID
import one.ztd.workbench.identity.model.CreateTenantLoginMethodSettingCommand
import one.ztd.workbench.identity.model.TenantLoginMethodSettingRecord

interface TenantLoginMethodSettingRepository {
  suspend fun createTenantSetting(
    command: CreateTenantLoginMethodSettingCommand
  ): TenantLoginMethodSettingRecord

  suspend fun findTenantSetting(
    tenantId: UUID,
    loginMethodId: UUID,
  ): TenantLoginMethodSettingRecord?
}
