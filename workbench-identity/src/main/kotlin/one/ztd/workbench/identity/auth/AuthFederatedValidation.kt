package one.ztd.workbench.identity.auth

import one.ztd.workbench.identity.model.LoginMethodDefinitionRecord
import one.ztd.workbench.identity.model.LoginMethodKind
import one.ztd.workbench.identity.model.TenantLoginMethodSettingRecord
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode

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
