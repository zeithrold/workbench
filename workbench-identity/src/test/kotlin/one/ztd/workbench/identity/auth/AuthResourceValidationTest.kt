package one.ztd.workbench.identity.auth

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import one.ztd.workbench.identity.LoginMethodRepository
import one.ztd.workbench.identity.TenantLoginMethodSettingRepository
import one.ztd.workbench.identity.model.LoginMethodDefinitionRecord
import one.ztd.workbench.identity.model.LoginMethodKind
import one.ztd.workbench.identity.model.TenantLoginMethodSettingRecord
import one.ztd.workbench.kernel.common.errors.AuthenticationFailedException
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.tenant.TenantRepository
import one.ztd.workbench.tenant.model.TenantRecord
import one.ztd.workbench.tenant.tenantconfig.TenantConfigService
import one.ztd.workbench.tenant.tenantconfig.model.MailSmtpTenantConfig
import one.ztd.workbench.tenant.tenantconfig.model.TenantConfigSpecs

class AuthResourceValidationTest :
  StringSpec({
    val tenants = mockk<TenantRepository>()
    val loginMethods = mockk<LoginMethodRepository>()
    val tenantLoginSettings = mockk<TenantLoginMethodSettingRepository>()
    val loginStates = mockk<AuthLoginStateRepository>()
    val tenantConfig = mockk<TenantConfigService>()
    val tenantId = UUID.randomUUID()
    val tenant =
      TenantRecord(
        id = tenantId,
        apiId = PublicId.new("ten"),
        slug = "acme",
        name = "Acme",
        timezone = "UTC",
        locale = "en-US",
        createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
      )
    val method =
      LoginMethodDefinitionRecord(
        id = UUID.randomUUID(),
        apiId = PublicId.new("lmd"),
        code = "password",
        kind = LoginMethodKind.PASSWORD,
        name = "Password",
        isBuiltin = true,
        isEnabledGlobally = true,
        createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
      )
    val setting =
      TenantLoginMethodSettingRecord(
        id = UUID.randomUUID(),
        tenantId = tenantId,
        loginMethodId = method.id,
        isEnabled = true,
        allowSignup = true,
        displayOrder = 0,
        config = JsonObject(emptyMap()),
        secretRef = null,
        createdBy = null,
        updatedBy = null,
        createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
      )

    "requireTenantByApiId returns tenant" {
      coEvery { tenants.findByApiId(tenant.apiId.value) } returns tenant
      runBlocking { requireTenantByApiId(tenants, tenant.apiId.value) } shouldBe tenant
    }

    "requireTenantByApiId throws when missing" {
      coEvery { tenants.findByApiId("ten_missing") } returns null
      shouldThrow<InvalidRequestException> {
          runBlocking { requireTenantByApiId(tenants, "ten_missing") }
        }
        .errorCode shouldBe WorkbenchErrorCode.RESOURCE_TENANT_NOT_FOUND
    }

    "requireLoginMethodByApiId returns method" {
      coEvery { loginMethods.findLoginMethodByApiId(method.apiId.value) } returns method
      runBlocking { requireLoginMethodByApiId(loginMethods, method.apiId.value) } shouldBe method
    }

    "requireTenantLoginSetting throws when missing" {
      coEvery { tenantLoginSettings.findTenantSetting(tenantId, method.id) } returns null
      shouldThrow<InvalidRequestException> {
          runBlocking { requireTenantLoginSetting(tenantLoginSettings, tenantId, method.id) }
        }
        .errorCode shouldBe WorkbenchErrorCode.IDENTITY_TENANT_LOGIN_SETTINGS_NOT_FOUND
    }

    "requireEnabledTenantSetting throws when disabled" {
      shouldThrow<InvalidRequestException> {
          requireEnabledTenantSetting(setting.copy(isEnabled = false))
        }
        .errorCode shouldBe WorkbenchErrorCode.IDENTITY_MAGIC_LINK_DISABLED
    }

    "requireMagicLinkMethod rejects non magic link methods" {
      shouldThrow<InvalidRequestException> {
          requireMagicLinkMethod(method, method.apiId.value)
        }
        .errorCode shouldBe WorkbenchErrorCode.IDENTITY_LOGIN_METHOD_NOT_MAGIC_LINK
    }

    "requireMagicLinkMailConfig throws when mail disabled" {
      coEvery { tenantConfig.get(tenantId, TenantConfigSpecs.MailSmtp) } returns
        MailSmtpTenantConfig(enabled = false)
      shouldThrow<InvalidRequestException> {
          runBlocking { requireMagicLinkMailConfig(tenantConfig, tenantId) }
        }
        .errorCode shouldBe WorkbenchErrorCode.IDENTITY_MAGIC_LINK_MAIL_NOT_CONFIGURED
    }

    "requireLdapMethod rejects non ldap methods" {
      shouldThrow<InvalidRequestException> {
          requireLdapMethod(method, method.apiId.value)
        }
        .errorCode shouldBe WorkbenchErrorCode.IDENTITY_LOGIN_METHOD_NOT_LDAP
    }

    "requireEnabledLdapSetting throws auth failure when disabled" {
      shouldThrow<AuthenticationFailedException> {
          requireEnabledLdapSetting(setting.copy(isEnabled = false))
        }
        .errorCode shouldBe WorkbenchErrorCode.AUTH_INVALID_CREDENTIALS
    }

    "requireActiveOAuthLoginState throws when state missing" {
      val now = OffsetDateTime.now(ZoneOffset.UTC)
      coEvery { loginStates.findActiveByStateHash("hash", now) } returns null
      shouldThrow<InvalidRequestException> {
          runBlocking { requireActiveOAuthLoginState(loginStates, "hash", now) }
        }
        .errorCode shouldBe WorkbenchErrorCode.IDENTITY_FEDERATED_OAUTH_STATE_INVALID
    }
  })
