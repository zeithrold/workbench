package doa.ink.workbench.service.identity.auth

import doa.ink.workbench.core.common.errors.AuthenticationFailedException
import doa.ink.workbench.core.identity.model.LoginCommand
import doa.ink.workbench.core.identity.model.LoginMethodKind
import doa.ink.workbench.data.identity.ExposedLoginAccountRepository
import doa.ink.workbench.data.identity.ExposedTenantRepository
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
      val accounts = ExposedLoginAccountRepository(database)
      val method = accounts.findLoginMethodByApiId(fixture.loginMethodApiId)!!
      val setting = accounts.findTenantSetting(fixture.tenant.tenantId, method.id)!!
      val subject =
        LdapAuthClient()
          .authenticate(setting, LdapTestContainer.TEST_USER, LdapTestContainer.TEST_PASSWORD)
      subject shouldBe LdapTestContainer.TEST_USER
    }

    "LdapAuthClient rejects invalid credentials" {
      val accounts = ExposedLoginAccountRepository(database)
      val method = accounts.findLoginMethodByApiId(fixture.loginMethodApiId)!!
      val setting = accounts.findTenantSetting(fixture.tenant.tenantId, method.id)!!
      shouldThrow<AuthenticationFailedException> {
        LdapAuthClient().authenticate(setting, LdapTestContainer.TEST_USER, "wrong-password")
      }
    }

    "LdapLoginAuthenticator completes login for a linked account" {
      runBlocking {
        val authenticator =
          LdapLoginAuthenticator(
            loginAccounts = ExposedLoginAccountRepository(database),
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
        val authenticator =
          LdapLoginAuthenticator(
            loginAccounts = ExposedLoginAccountRepository(database),
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
