package ink.doa.workbench.security.identity.auth

import ink.doa.workbench.core.common.errors.AuthenticationFailedException
import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.identity.LoginMethodRepository
import ink.doa.workbench.core.identity.TenantLoginMethodSettingRepository
import ink.doa.workbench.core.identity.TenantRepository
import ink.doa.workbench.core.identity.auth.AuthLoginStateRepository
import ink.doa.workbench.core.identity.model.AuthLoginStateRecord
import ink.doa.workbench.core.identity.model.LoginMethodDefinitionRecord
import ink.doa.workbench.core.identity.model.LoginMethodKind
import ink.doa.workbench.core.identity.model.TenantLoginMethodSettingRecord
import ink.doa.workbench.core.identity.model.TenantRecord
import ink.doa.workbench.core.tenantconfig.model.MailSmtpTenantConfig
import ink.doa.workbench.core.tenantconfig.model.TenantConfigSpecs
import ink.doa.workbench.tenant.tenantconfig.TenantConfigService
import java.time.OffsetDateTime
import java.util.UUID

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
