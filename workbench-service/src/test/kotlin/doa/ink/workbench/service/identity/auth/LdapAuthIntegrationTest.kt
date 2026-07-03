package doa.ink.workbench.service.identity.auth

import doa.ink.workbench.core.common.errors.AuthenticationFailedException
import doa.ink.workbench.core.identity.model.LoginCommand
import doa.ink.workbench.core.identity.model.LoginMethodKind
import doa.ink.workbench.data.identity.ExposedLoginAccountStore
import doa.ink.workbench.data.identity.ExposedLoginMethodRepository
import doa.ink.workbench.data.identity.ExposedTenantLoginMethodSettingRepository
import doa.ink.workbench.data.identity.ExposedTenantRepository
import doa.ink.workbench.data.identity.ExposedUserLoginAccountRepository
import doa.ink.workbench.service.identity.auth.support.AuthIntegrationFixtures
import doa.ink.workbench.service.identity.auth.support.LdapAuthFixture
import doa.ink.workbench.service.identity.auth.support.LdapTestContainer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.Tag
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer

@Tag("integration")
class LdapAuthIntegrationTest :
  StringSpec({
    val ldap: GenericContainer<*> = LdapTestContainer.create()
    val postgres: PostgreSQLContainer<*> = AuthIntegrationFixtures.startPostgres()
    lateinit var database: Database
    lateinit var fixture: LdapAuthFixture

    beforeSpec {
      LdapTestContainer.startAndBootstrap(ldap)
      database = AuthIntegrationFixtures.connectDatabase(postgres)
      fixture = runBlocking { AuthIntegrationFixtures.seedLdapFixture(database, ldap) }
    }

    afterSpec {
      ldap.stop()
      postgres.stop()
    }

    "LdapAuthClient authenticates valid credentials against OpenLDAP" {
      val loginMethods = ExposedLoginMethodRepository(database)
      val tenantLoginSettings = ExposedTenantLoginMethodSettingRepository(database)
      val method = loginMethods.findLoginMethodByApiId(fixture.loginMethodApiId)!!
      val setting = tenantLoginSettings.findTenantSetting(fixture.tenant.tenantId, method.id)!!
      val subject =
        LdapAuthClient()
          .authenticate(setting, LdapTestContainer.TEST_USER, LdapTestContainer.TEST_PASSWORD)
      subject shouldBe LdapTestContainer.TEST_USER
    }

    "LdapAuthClient rejects invalid credentials" {
      val loginMethods = ExposedLoginMethodRepository(database)
      val tenantLoginSettings = ExposedTenantLoginMethodSettingRepository(database)
      val method = loginMethods.findLoginMethodByApiId(fixture.loginMethodApiId)!!
      val setting = tenantLoginSettings.findTenantSetting(fixture.tenant.tenantId, method.id)!!
      shouldThrow<AuthenticationFailedException> {
        LdapAuthClient().authenticate(setting, LdapTestContainer.TEST_USER, "wrong-password")
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
              subject = LdapTestContainer.TEST_USER,
              password = LdapTestContainer.TEST_PASSWORD,
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
              subject = "unlinkeduser",
              password = "unlinkedpass",
            )
          )
        }
      }
    }
  })
