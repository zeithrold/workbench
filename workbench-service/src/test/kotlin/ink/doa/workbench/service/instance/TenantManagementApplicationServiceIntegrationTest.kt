package ink.doa.workbench.service.instance

import ink.doa.workbench.core.common.errors.ResourceConflictException
import ink.doa.workbench.core.identity.model.CreateTenantCommand
import ink.doa.workbench.data.repository.identity.ExposedLoginMethodRepository
import ink.doa.workbench.data.repository.identity.ExposedTenantLoginMethodSettingRepository
import ink.doa.workbench.data.repository.identity.ExposedTenantRepository
import ink.doa.workbench.security.identity.TenantLoginMethodService
import ink.doa.workbench.security.identity.UserLookupService
import ink.doa.workbench.security.identity.auth.support.AuthIntegrationFixtures
import ink.doa.workbench.service.instance.support.UnusedPublicIdResolverDependencies
import ink.doa.workbench.tenant.tenant.TenantService
import ink.doa.workbench.testsupport.postgres.PostgresTestDatabaseLease
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database

@Tags("integration")
class TenantManagementApplicationServiceIntegrationTest :
  StringSpec({
    val postgresLease: PostgresTestDatabaseLease = AuthIntegrationFixtures.openSpecDatabase()
    lateinit var database: Database
    lateinit var tenants: ExposedTenantRepository
    lateinit var loginMethods: ExposedLoginMethodRepository
    lateinit var tenantLoginSettings: ExposedTenantLoginMethodSettingRepository
    lateinit var service: TenantManagementApplicationService

    beforeSpec {
      database = postgresLease.database
      val clock = Clock.fixed(Instant.parse("2026-07-03T00:00:00Z"), ZoneOffset.UTC)
      tenants =
        ExposedTenantRepository(
          database,
          ink.doa.workbench.data.messaging.ExposedDomainEventOutbox(
            database,
            ink.doa.workbench.core.messaging.DomainEventEncoder(clock),
          ),
        )
      loginMethods = ExposedLoginMethodRepository(database)
      tenantLoginSettings = ExposedTenantLoginMethodSettingRepository(database)
      val deps = UnusedPublicIdResolverDependencies(loginMethods = loginMethods)
      service =
        TenantManagementApplicationService(
          dependencies =
            TenantManagementDependencies(
              tenants = TenantService(tenants),
              tenantLoginMethods = TenantLoginMethodService(loginMethods, tenantLoginSettings),
              userLookupService = UserLookupService(deps.users),
              adminUserService = mockk(relaxed = true),
              invitationService = mockk(relaxed = true),
              defaultWorkItemTemplate = mockk(relaxed = true),
              clock = clock,
            )
        )
    }

    afterSpec {
      postgresLease.close()
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
