package ink.doa.workbench.identity

import ink.doa.workbench.identity.model.CreateTenantLoginMethodSettingCommand
import ink.doa.workbench.identity.model.LoginMethodDefinitionRecord
import ink.doa.workbench.identity.model.LoginMethodKind
import ink.doa.workbench.identity.model.TenantLoginMethodSettingRecord
import ink.doa.workbench.kernel.common.errors.ResourceNotFoundException
import ink.doa.workbench.kernel.common.ids.PublicId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class TenantLoginMethodServiceTest :
  StringSpec({
    val loginMethods = mockk<LoginMethodRepository>()
    val tenantLoginSettings = mockk<TenantLoginMethodSettingRepository>()
    val service = TenantLoginMethodService(loginMethods, tenantLoginSettings)
    val tenantId = UUID.randomUUID()
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val passwordMethod =
      LoginMethodDefinitionRecord(
        id = UUID.randomUUID(),
        apiId = PublicId.new("lgn"),
        code = "password",
        kind = LoginMethodKind.PASSWORD,
        name = "Password",
        isBuiltin = true,
        isEnabledGlobally = true,
        createdAt = now,
        updatedAt = now,
      )
    val existingSetting =
      TenantLoginMethodSettingRecord(
        id = UUID.randomUUID(),
        tenantId = tenantId,
        loginMethodId = passwordMethod.id,
        isEnabled = true,
        allowSignup = false,
        displayOrder = 0,
        secretRef = null,
        createdBy = null,
        updatedBy = null,
        createdAt = now,
        updatedAt = now,
      )

    beforeTest { clearAllMocks() }

    "enablePasswordLoginMethod creates setting when missing" {
      coEvery { loginMethods.findLoginMethodByCode("password") } returns passwordMethod
      coEvery { tenantLoginSettings.findTenantSetting(tenantId, passwordMethod.id) } returns null
      coEvery { tenantLoginSettings.createTenantSetting(any()) } returns existingSetting

      service.enablePasswordLoginMethod(tenantId)

      coVerify {
        tenantLoginSettings.createTenantSetting(
          CreateTenantLoginMethodSettingCommand(
            tenantId = tenantId,
            loginMethodId = passwordMethod.id,
          )
        )
      }
    }

    "enablePasswordLoginMethod is idempotent when setting exists" {
      coEvery { loginMethods.findLoginMethodByCode("password") } returns passwordMethod
      coEvery { tenantLoginSettings.findTenantSetting(tenantId, passwordMethod.id) } returns
        existingSetting

      service.enablePasswordLoginMethod(tenantId)

      coVerify(exactly = 0) { tenantLoginSettings.createTenantSetting(any()) }
    }

    "enablePasswordLoginMethod throws when password method is missing" {
      coEvery { loginMethods.findLoginMethodByCode("password") } returns null

      shouldThrow<ResourceNotFoundException> { service.enablePasswordLoginMethod(tenantId) }
    }
  })
