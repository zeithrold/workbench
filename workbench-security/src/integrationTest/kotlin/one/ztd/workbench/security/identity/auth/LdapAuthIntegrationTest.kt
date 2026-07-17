package one.ztd.workbench.security.identity.auth

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import one.ztd.workbench.data.repository.identity.ExposedLoginAccountStore
import one.ztd.workbench.data.repository.identity.ExposedLoginMethodRepository
import one.ztd.workbench.data.repository.identity.ExposedTenantLoginMethodSettingRepository
import one.ztd.workbench.data.repository.identity.ExposedTenantRepository
import one.ztd.workbench.data.repository.identity.ExposedUserLoginAccountRepository
import one.ztd.workbench.identity.model.LoginCommand
import one.ztd.workbench.identity.model.LoginMethodKind
import one.ztd.workbench.kernel.common.errors.AuthenticationFailedException
import one.ztd.workbench.security.identity.auth.support.AuthIntegrationFixtures
import one.ztd.workbench.security.identity.auth.support.InMemoryLdapTestServer
import one.ztd.workbench.security.identity.auth.support.LdapAuthFixture
import one.ztd.workbench.testsupport.postgres.PostgresTestDatabaseLease
import org.jetbrains.exposed.v1.jdbc.Database

class LdapAuthIntegrationTest :
  StringSpec({
    val ldap: InMemoryLdapTestServer = InMemoryLdapTestServer.start()
    val postgresLease: PostgresTestDatabaseLease = AuthIntegrationFixtures.openSpecDatabase()
    lateinit var database: Database
    lateinit var fixture: LdapAuthFixture

    beforeSpec {
      database = postgresLease.database
      fixture = runBlocking { AuthIntegrationFixtures.seedLdapFixture(database, ldap) }
    }

    afterSpec {
      ldap.close()
      postgresLease.close()
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
