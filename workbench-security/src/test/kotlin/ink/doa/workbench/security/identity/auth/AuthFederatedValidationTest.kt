package ink.doa.workbench.security.identity.auth

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.model.LoginMethodDefinitionRecord
import ink.doa.workbench.core.identity.model.LoginMethodKind
import ink.doa.workbench.core.identity.model.TenantLoginMethodSettingRecord
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.JsonObject

class AuthFederatedValidationTest :
  StringSpec({
    val methodId = UUID.randomUUID()
    val tenantId = UUID.randomUUID()
    val oidcMethod =
      LoginMethodDefinitionRecord(
        id = methodId,
        apiId = PublicId.new("lmd"),
        code = "oidc",
        kind = LoginMethodKind.OIDC,
        name = "OIDC",
        isBuiltin = false,
        isEnabledGlobally = true,
        createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
      )
    val passwordMethod = oidcMethod.copy(kind = LoginMethodKind.PASSWORD, code = "password")
    val enabledSetting =
      TenantLoginMethodSettingRecord(
        id = UUID.randomUUID(),
        tenantId = tenantId,
        loginMethodId = methodId,
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

    "requireFederatedMethod accepts oauth protocols" {
      requireFederatedMethod(oidcMethod, oidcMethod.apiId.value)
      requireFederatedMethod(oidcMethod.copy(kind = LoginMethodKind.OAUTH2), oidcMethod.apiId.value)
      requireFederatedMethod(oidcMethod.copy(kind = LoginMethodKind.SAML), oidcMethod.apiId.value)
    }

    "requireFederatedMethod rejects password methods" {
      shouldThrow<InvalidRequestException> {
          requireFederatedMethod(passwordMethod, passwordMethod.apiId.value)
        }
        .errorCode shouldBe WorkbenchErrorCode.IDENTITY_FEDERATED_PROTOCOL_UNSUPPORTED
    }

    "requireEnabledFederatedSetting returns enabled setting" {
      requireEnabledFederatedSetting(enabledSetting) shouldBe enabledSetting
    }

    "requireEnabledFederatedSetting throws when disabled" {
      shouldThrow<InvalidRequestException> {
          requireEnabledFederatedSetting(enabledSetting.copy(isEnabled = false))
        }
        .errorCode shouldBe WorkbenchErrorCode.IDENTITY_LOGIN_METHOD_DISABLED
    }

    "requireEnabledFederatedSetting throws when missing" {
      shouldThrow<InvalidRequestException> {
          requireEnabledFederatedSetting(null)
        }
        .errorCode shouldBe WorkbenchErrorCode.IDENTITY_LOGIN_METHOD_DISABLED
    }
  })
