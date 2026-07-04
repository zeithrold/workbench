package ink.doa.workbench.data.identity

import ink.doa.workbench.core.identity.model.CreateLoginAccountCommand
import ink.doa.workbench.core.identity.model.CreateTenantLoginMethodSettingCommand
import ink.doa.workbench.core.identity.model.CreateTenantMemberCommand
import ink.doa.workbench.core.identity.model.CreateUserCommand
import ink.doa.workbench.core.identity.model.LinkUserLoginAccountCommand
import ink.doa.workbench.core.identity.model.TenantMemberStatus
import ink.doa.workbench.data.support.seedTenant
import ink.doa.workbench.data.support.withPostgresDatabase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.junit.jupiter.api.Tag

@Tag("integration")
class ExposedLoginDiscoveryRepositoryIntegrationTest :
  StringSpec({
    "listLoginOptionsForIdentifier returns tenant password option for member" {
      withPostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val users = ExposedUserRepository(database)
        val members = ExposedTenantMemberRepository(database)
        val loginMethods = ExposedLoginMethodRepository(database)
        val tenantLoginSettings = ExposedTenantLoginMethodSettingRepository(database)
        val loginAccounts = ExposedLoginAccountStore(database)
        val userLoginAccounts = ExposedUserLoginAccountRepository(database)
        val repository = ExposedLoginDiscoveryRepository(database, loginAccounts, userLoginAccounts)

        val user = users.create(CreateUserCommand("Ada", "ada@example.test"))
        members.create(
          CreateTenantMemberCommand(
            tenantId = tenantId,
            userId = user.id,
            status = TenantMemberStatus.ACTIVE,
            joinedAt = OffsetDateTime.now(ZoneOffset.UTC),
          )
        )
        val method = loginMethods.findLoginMethodByCode("password")!!
        tenantLoginSettings.createTenantSetting(
          CreateTenantLoginMethodSettingCommand(tenantId = tenantId, loginMethodId = method.id)
        )
        val loginAccount =
          loginAccounts.createLoginAccount(
            CreateLoginAccountCommand(
              loginMethodId = method.id,
              subject = "ada@example.test",
              normalizedSubject = "ada@example.test",
              displayName = "Ada",
            )
          )
        userLoginAccounts.linkUser(
          LinkUserLoginAccountCommand(userId = user.id, loginAccountId = loginAccount.id)
        )

        val options = repository.listLoginOptionsForIdentifier("ada@example.test")

        options.shouldHaveSize(1)
        options.single().loginMethod.code shouldBe "password"
        options.single().tenant.slug.startsWith("test-") shouldBe true
      }
    }

    "listLoginOptionsForIdentifier returns empty list for unknown email" {
      withPostgresDatabase { database ->
        val repository =
          ExposedLoginDiscoveryRepository(
            database,
            ExposedLoginAccountStore(database),
            ExposedUserLoginAccountRepository(database),
          )

        repository.listLoginOptionsForIdentifier("missing@example.test").shouldHaveSize(0)
      }
    }
  })
