package one.ztd.workbench.identity.auth

import java.time.OffsetDateTime
import java.util.UUID
import one.ztd.workbench.identity.LoginMethodRepository
import one.ztd.workbench.identity.TenantLoginMethodSettingRepository
import one.ztd.workbench.identity.model.AuthLoginStateRecord
import one.ztd.workbench.identity.model.LoginMethodDefinitionRecord
import one.ztd.workbench.identity.model.LoginMethodKind
import one.ztd.workbench.identity.model.TenantLoginMethodSettingRecord
import one.ztd.workbench.kernel.common.errors.AuthenticationFailedException
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.tenant.TenantRepository
import one.ztd.workbench.tenant.model.TenantRecord
import one.ztd.workbench.tenant.tenantconfig.TenantConfigService
import one.ztd.workbench.tenant.tenantconfig.model.MailSmtpTenantConfig
import one.ztd.workbench.tenant.tenantconfig.model.TenantConfigSpecs

fun authInvalidCredentials(): Nothing =
  throw AuthenticationFailedException(WorkbenchErrorCode.AUTH_INVALID_CREDENTIALS)

suspend fun requireTenantByApiId(tenants: TenantRepository, tenantId: String): TenantRecord =
  tenants.findByApiId(tenantId)
    ?: throw InvalidRequestException(
      WorkbenchErrorCode.RESOURCE_TENANT_NOT_FOUND,
      "Unknown tenant: $tenantId",
    )

suspend fun requireLoginMethodByApiId(
  loginMethods: LoginMethodRepository,
  loginMethodId: String,
): LoginMethodDefinitionRecord =
  loginMethods.findLoginMethodByApiId(loginMethodId)
    ?: throw InvalidRequestException(
      WorkbenchErrorCode.RESOURCE_LOGIN_METHOD_NOT_FOUND,
      "Unknown login method: $loginMethodId",
    )

suspend fun requireLoginMethodByInternalId(
  loginMethods: LoginMethodRepository,
  loginMethodId: UUID,
): LoginMethodDefinitionRecord =
  loginMethods.findLoginMethodById(loginMethodId)
    ?: throw InvalidRequestException(WorkbenchErrorCode.RESOURCE_LOGIN_METHOD_NOT_FOUND)

suspend fun requireTenantLoginSetting(
  tenantLoginSettings: TenantLoginMethodSettingRepository,
  tenantId: UUID,
  loginMethodId: UUID,
): TenantLoginMethodSettingRecord =
  tenantLoginSettings.findTenantSetting(tenantId, loginMethodId)
    ?: throw InvalidRequestException(WorkbenchErrorCode.IDENTITY_TENANT_LOGIN_SETTINGS_NOT_FOUND)

suspend fun requireActiveOAuthLoginState(
  loginStates: AuthLoginStateRepository,
  stateHash: String,
  at: OffsetDateTime,
): AuthLoginStateRecord =
  loginStates.findActiveByStateHash(stateHash, at)
    ?: throw InvalidRequestException(WorkbenchErrorCode.IDENTITY_FEDERATED_OAUTH_STATE_INVALID)

fun requireEnabledTenantSetting(
  setting: TenantLoginMethodSettingRecord?
): TenantLoginMethodSettingRecord {
  if (setting?.isEnabled != true) {
    throw InvalidRequestException(WorkbenchErrorCode.IDENTITY_MAGIC_LINK_DISABLED)
  }
  return setting
}

fun requireMagicLinkMethod(method: LoginMethodDefinitionRecord, loginMethodId: String) {
  if (method.kind != LoginMethodKind.EMAIL_MAGIC_LINK) {
    throw InvalidRequestException(
      WorkbenchErrorCode.IDENTITY_LOGIN_METHOD_NOT_MAGIC_LINK,
      "Login method $loginMethodId is not email_magic_link.",
    )
  }
}

suspend fun requireMagicLinkMailConfig(
  tenantConfig: TenantConfigService,
  tenantId: UUID,
): MailSmtpTenantConfig {
  val mailConfig = tenantConfig.get(tenantId, TenantConfigSpecs.MailSmtp)
  if (!mailConfig.enabled) {
    throw InvalidRequestException(WorkbenchErrorCode.IDENTITY_MAGIC_LINK_MAIL_NOT_CONFIGURED)
  }
  return mailConfig
}

fun requireLdapMethod(method: LoginMethodDefinitionRecord, loginMethodId: String) {
  if (method.kind != LoginMethodKind.LDAP) {
    throw InvalidRequestException(
      WorkbenchErrorCode.IDENTITY_LOGIN_METHOD_NOT_LDAP,
      "Login method $loginMethodId is not LDAP.",
    )
  }
}

fun requireEnabledLdapSetting(setting: TenantLoginMethodSettingRecord?) {
  if (setting?.isEnabled != true) {
    authInvalidCredentials()
  }
}
