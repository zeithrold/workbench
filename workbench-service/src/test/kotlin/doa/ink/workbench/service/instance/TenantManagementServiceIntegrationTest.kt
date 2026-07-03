package doa.ink.workbench.service.instance

import doa.ink.workbench.core.common.errors.ResourceConflictException
import doa.ink.workbench.core.identity.model.CreateTenantCommand
import doa.ink.workbench.data.identity.ExposedLoginMethodRepository
import doa.ink.workbench.data.identity.ExposedTenantLoginMethodSettingRepository
import doa.ink.workbench.data.identity.ExposedTenantRepository
import doa.ink.workbench.service.common.PublicIdResolver
import doa.ink.workbench.service.identity.auth.support.AuthIntegrationFixtures
import doa.ink.workbench.service.instance.support.UnusedPublicIdResolverDependencies
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.Tag
import org.testcontainers.containers.PostgreSQLContainer

@Tag("integration")
class TenantManagementServiceIntegrationTest :
  StringSpec({
    val postgres: PostgreSQLContainer<*> = AuthIntegrationFixtures.startPostgres()
    lateinit var database: Database
    lateinit var tenants: ExposedTenantRepository
    lateinit var loginMethods: ExposedLoginMethodRepository
    lateinit var tenantLoginSettings: ExposedTenantLoginMethodSettingRepository
    lateinit var service: TenantManagementService

    beforeSpec {
      database = AuthIntegrationFixtures.connectDatabase(postgres)
      tenants = ExposedTenantRepository(database)
      loginMethods = ExposedLoginMethodRepository(database)
      tenantLoginSettings = ExposedTenantLoginMethodSettingRepository(database)
      val deps = UnusedPublicIdResolverDependencies(loginMethods = loginMethods)
      service =
        TenantManagementService(
          tenants = tenants,
          loginMethods = loginMethods,
          tenantLoginSettings = tenantLoginSettings,
          publicIds =
            PublicIdResolver(
              tenants = tenants,
              users = deps.users,
              loginMethods = loginMethods,
              bearerTokens = deps.bearerTokens,
              adminUserQueries = deps.adminUserQueries,
              accessGrants = deps.accessGrants,
              projects = deps.projects,
            ),
          adminUserService = mockk(relaxed = true),
          invitationService = mockk(relaxed = true),
        )
    }

    afterSpec {
      postgres.stop()
    }

    "create tenant provisions password login method setting" {
      runBlocking {
        val tenant =
          service.create(
            CreateTenantCommand(
              name = "Acme",
              slug = "acme-${UUID.randomUUID().toString().take(8)}",
              timezone = "UTC",
              locale = "en-US",
            )
          )
        val passwordMethod = loginMethods.findLoginMethodByCode("password").shouldNotBeNull()
        val setting =
          tenantLoginSettings.findTenantSetting(tenant.id, passwordMethod.id).shouldNotBeNull()
        setting.isEnabled shouldBe true
      }
    }

    "list and update tenant" {
      runBlocking {
        val slug = "beta-${UUID.randomUUID().toString().take(8)}"
        val created =
          service.create(
            CreateTenantCommand(name = "Beta", slug = slug, timezone = "UTC", locale = "en-US")
          )
        service.list(slug) shouldHaveSize 1
        val updated =
          service.update(
            tenantPublicId = created.apiId.value,
            name = "Beta Corp",
            slug = null,
            timezone = "Asia/Shanghai",
            locale = null,
          )
        updated.name shouldBe "Beta Corp"
        updated.timezone shouldBe "Asia/Shanghai"
        updated.slug shouldBe slug
      }
    }

    "create rejects duplicate slug" {
      runBlocking {
        val slug = "gamma-${UUID.randomUUID().toString().take(8)}"
        service.create(
          CreateTenantCommand(name = "Gamma", slug = slug, timezone = "UTC", locale = "en-US")
        )
        shouldThrow<ResourceConflictException> {
          service.create(
            CreateTenantCommand(name = "Gamma 2", slug = slug, timezone = "UTC", locale = "en-US")
          )
        }
      }
    }
  })
