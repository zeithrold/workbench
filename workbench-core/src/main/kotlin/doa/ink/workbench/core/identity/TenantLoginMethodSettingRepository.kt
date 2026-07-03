package doa.ink.workbench.core.identity

import doa.ink.workbench.core.identity.model.CreateTenantLoginMethodSettingCommand
import doa.ink.workbench.core.identity.model.TenantLoginMethodSettingRecord
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
