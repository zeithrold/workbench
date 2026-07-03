package doa.ink.workbench.service.instance

import doa.ink.workbench.core.common.errors.ResourceNotFoundException
import doa.ink.workbench.core.identity.LoginAccountRepository
import doa.ink.workbench.core.identity.TenantRepository
import doa.ink.workbench.core.identity.model.CreateTenantCommand
import doa.ink.workbench.core.identity.model.CreateTenantLoginMethodSettingCommand
import doa.ink.workbench.core.identity.model.TenantRecord
import doa.ink.workbench.core.identity.model.UpdateTenantCommand
import doa.ink.workbench.service.common.PublicIdResolver
import java.util.UUID
import org.springframework.stereotype.Service

private const val PASSWORD_METHOD_CODE = "password"

@Service
class TenantManagementService(
  private val tenants: TenantRepository,
  private val loginAccounts: LoginAccountRepository,
  private val publicIds: PublicIdResolver,
) {
  suspend fun list(slug: String? = null): List<TenantRecord> = tenants.list(slug)

  suspend fun get(tenantPublicId: String): TenantRecord = publicIds.resolveTenant(tenantPublicId)

  suspend fun create(command: CreateTenantCommand): TenantRecord {
    val tenant = tenants.create(command)
    enablePasswordLoginMethod(tenant.id)
    return tenant
  }

  suspend fun update(
    tenantPublicId: String,
    name: String?,
    slug: String?,
    timezone: String?,
    locale: String?,
  ): TenantRecord {
    val tenant = publicIds.resolveTenant(tenantPublicId)
    return tenants.update(
      UpdateTenantCommand(
        tenantId = tenant.id,
        name = name,
        slug = slug,
        timezone = timezone,
        locale = locale,
      )
    )
  }

  private suspend fun enablePasswordLoginMethod(tenantId: UUID) {
    val passwordMethod =
      loginAccounts.findLoginMethodByCode(PASSWORD_METHOD_CODE)
        ?: throw ResourceNotFoundException("Password login method is not configured.")
    if (loginAccounts.findTenantSetting(tenantId, passwordMethod.id) == null) {
      loginAccounts.createTenantSetting(
        CreateTenantLoginMethodSettingCommand(
          tenantId = tenantId,
          loginMethodId = passwordMethod.id,
        )
      )
    }
  }
}
