package ink.doa.workbench.security.identity.auth

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.identity.model.LoginMethodDefinitionRecord
import ink.doa.workbench.core.identity.model.LoginMethodKind
import ink.doa.workbench.core.identity.model.TenantLoginMethodSettingRecord

fun requireFederatedMethod(method: LoginMethodDefinitionRecord, loginMethodId: String) {
  if (method.kind !in setOf(LoginMethodKind.OAUTH2, LoginMethodKind.OIDC, LoginMethodKind.SAML)) {
    throw InvalidRequestException(
      WorkbenchErrorCode.IDENTITY_FEDERATED_PROTOCOL_UNSUPPORTED,
      "Login method $loginMethodId does not support federated authorize.",
    )
  }
}

fun requireEnabledFederatedSetting(
  setting: TenantLoginMethodSettingRecord?
): TenantLoginMethodSettingRecord {
  if (setting?.isEnabled != true) {
    throw InvalidRequestException(WorkbenchErrorCode.IDENTITY_LOGIN_METHOD_DISABLED)
  }
  return setting
}
