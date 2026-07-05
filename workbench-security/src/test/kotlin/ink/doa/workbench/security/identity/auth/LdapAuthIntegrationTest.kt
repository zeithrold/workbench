package ink.doa.workbench.security.identity.auth

import ink.doa.workbench.core.common.errors.AuthenticationFailedException
import ink.doa.workbench.core.identity.model.LoginCommand
import ink.doa.workbench.core.identity.model.LoginMethodKind
import ink.doa.workbench.data.repository.identity.ExposedLoginAccountStore
import ink.doa.workbench.data.repository.identity.ExposedLoginMethodRepository
import ink.doa.workbench.data.repository.identity.ExposedTenantLoginMethodSettingRepository
import ink.doa.workbench.data.repository.identity.ExposedTenantRepository
import ink.doa.workbench.data.repository.identity.ExposedUserLoginAccountRepository
import ink.doa.workbench.security.identity.auth.support.AuthIntegrationFixtures
import ink.doa.workbench.security.identity.auth.support.InMemoryLdapTestServer
import ink.doa.workbench.security.identity.auth.support.LdapAuthFixture
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.Tag
import org.testcontainers.containers.PostgreSQLContainer

@Tag("integration")
class LdapAuthIntegrationTest :
  StringSpec({
    val ldap: InMemoryLdapTestServer = InMemoryLdapTestServer.start()
    val postgres: PostgreSQLContainer<*> = AuthIntegrationFixtures.startPostgres()
    lateinit var database: Database
    lateinit var fixture: LdapAuthFixture

    beforeSpec {
      database = AuthIntegrationFixtures.connectDatabase(postgres)
      fixture = runBlocking { AuthIntegrationFixtures.seedLdapFixture(database, ldap) }
    }

    afterSpec {
      ldap.close()
      postgres.stop()
    }

    "LdapAuthClient authenticates valid credentials against in-memory LDAP" {
      val loginMethods = ExposedLoginMethodRepository(database)
      val tenantLoginSettings = ExposedTenantLoginMethodSettingRepository(database)
      val method = loginMethods.findLoginMethodByApiId(fixture.loginMethodApiId)!!
      val setting = tenantLoginSettings.findTenantSetting(fixture.tenant.tenantId, method.id)!!
      val subject =
        LdapAuthClient()
          .authenticate(
            setting,
            InMemoryLdapTestServer.TEST_USER,
            InMemoryLdapTestServer.TEST_PASSWORD,
          )
      subject shouldBe InMemoryLdapTestServer.TEST_USER
    }

    "LdapAuthClient rejects invalid credentials" {
      val loginMethods = ExposedLoginMethodRepository(database)
      val tenantLoginSettings = ExposedTenantLoginMethodSettingRepository(database)
      val method = loginMethods.findLoginMethodByApiId(fixture.loginMethodApiId)!!
      val setting = tenantLoginSettings.findTenantSetting(fixture.tenant.tenantId, method.id)!!
      shouldThrow<AuthenticationFailedException> {
        LdapAuthClient().authenticate(setting, InMemoryLdapTestServer.TEST_USER, "wrong-password")
      }
    }

    "LdapLoginAuthenticator completes login for a linked account" {
      runBlocking {
        val loginMethods = ExposedLoginMethodRepository(database)
        val tenantLoginSettings = ExposedTenantLoginMethodSettingRepository(database)
        val loginAccounts = ExposedLoginAccountStore(database)
        val userLoginAccounts = ExposedUserLoginAccountRepository(database)
        val authenticator =
          LdapLoginAuthenticator(
            loginMethods = loginMethods,
            tenantLoginSettings = tenantLoginSettings,
            loginAccounts = loginAccounts,
            userLoginAccounts = userLoginAccounts,
            tenants = ExposedTenantRepository(database),
            ldapClient = LdapAuthClient(),
          )
        val identity =
          authenticator.authenticate(
            LoginCommand(
              method = LoginMethodKind.LDAP,
              tenantId = fixture.tenant.tenantApiId,
              loginMethodId = fixture.loginMethodApiId,
              subject = InMemoryLdapTestServer.TEST_USER,
              password = InMemoryLdapTestServer.TEST_PASSWORD,
            )
          )
        identity.user.id shouldBe fixture.linkedUserId
      }
    }

    "LdapLoginAuthenticator rejects LDAP bind when account is not linked" {
      runBlocking {
        val loginMethods = ExposedLoginMethodRepository(database)
        val tenantLoginSettings = ExposedTenantLoginMethodSettingRepository(database)
        val loginAccounts = ExposedLoginAccountStore(database)
        val userLoginAccounts = ExposedUserLoginAccountRepository(database)
        val authenticator =
          LdapLoginAuthenticator(
            loginMethods = loginMethods,
            tenantLoginSettings = tenantLoginSettings,
            loginAccounts = loginAccounts,
            userLoginAccounts = userLoginAccounts,
            tenants = ExposedTenantRepository(database),
            ldapClient = LdapAuthClient(),
          )
        shouldThrow<AuthenticationFailedException> {
          authenticator.authenticate(
            LoginCommand(
              method = LoginMethodKind.LDAP,
              tenantId = fixture.tenant.tenantApiId,
              loginMethodId = fixture.loginMethodApiId,
              subject = InMemoryLdapTestServer.UNLINKED_USER,
              password = InMemoryLdapTestServer.UNLINKED_PASSWORD,
            )
          )
        }
      }
    }
  })
