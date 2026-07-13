package ink.doa.workbench.identity

import ink.doa.workbench.identity.model.CreateTenantLoginMethodSettingCommand
import ink.doa.workbench.kernel.common.errors.ResourceNotFoundException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
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
