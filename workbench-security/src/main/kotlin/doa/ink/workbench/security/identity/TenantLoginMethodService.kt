package doa.ink.workbench.security.identity

import doa.ink.workbench.core.common.errors.ResourceNotFoundException
import doa.ink.workbench.core.common.errors.WorkbenchErrorCode
import doa.ink.workbench.core.identity.LoginMethodRepository
import doa.ink.workbench.core.identity.TenantLoginMethodSettingRepository
import doa.ink.workbench.core.identity.model.CreateTenantLoginMethodSettingCommand
import java.util.UUID
import org.springframework.stereotype.Service

private const val PASSWORD_METHOD_CODE = "password"

@Service
class TenantLoginMethodService(
  private val loginMethods: LoginMethodRepository,
  private val tenantLoginSettings: TenantLoginMethodSettingRepository,
) {
  suspend fun enablePasswordLoginMethod(tenantId: UUID) {
    val passwordMethod =
      loginMethods.findLoginMethodByCode(PASSWORD_METHOD_CODE)
        ?: throw ResourceNotFoundException(
          WorkbenchErrorCode.RESOURCE_PASSWORD_LOGIN_METHOD_NOT_FOUND
        )
    if (tenantLoginSettings.findTenantSetting(tenantId, passwordMethod.id) == null) {
      tenantLoginSettings.createTenantSetting(
        CreateTenantLoginMethodSettingCommand(
          tenantId = tenantId,
          loginMethodId = passwordMethod.id,
        )
      )
    }
  }
}
