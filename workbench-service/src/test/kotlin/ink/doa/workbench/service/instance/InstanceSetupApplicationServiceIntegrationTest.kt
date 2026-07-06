package ink.doa.workbench.service.instance

import ink.doa.workbench.core.identity.auth.PasswordHasher
import ink.doa.workbench.core.identity.model.BootstrapInstanceAdminCommand
import ink.doa.workbench.data.repository.identity.ExposedAuthEventRepository
import ink.doa.workbench.data.repository.identity.ExposedAuthSessionRepository
import ink.doa.workbench.data.repository.identity.ExposedBearerTokenRepository
import ink.doa.workbench.data.repository.identity.ExposedLoginAccountStore
import ink.doa.workbench.data.repository.identity.ExposedLoginDiscoveryRepository
import ink.doa.workbench.data.repository.identity.ExposedLoginMethodRepository
import ink.doa.workbench.data.repository.identity.ExposedTenantMemberRepository
import ink.doa.workbench.data.repository.identity.ExposedTenantRepository
import ink.doa.workbench.data.repository.identity.ExposedUserLoginAccountRepository
import ink.doa.workbench.data.repository.identity.ExposedUserRepository
import ink.doa.workbench.data.repository.permission.ExposedAccessGrantRepository
import ink.doa.workbench.data.repository.permission.ExposedAdminUserCommandRepository
import ink.doa.workbench.data.repository.permission.ExposedAdminUserQueryRepository
import ink.doa.workbench.security.common.PublicIdIdentitySupport
import ink.doa.workbench.security.common.PublicIdPermissionSupport
import ink.doa.workbench.security.common.PublicIdResolver
import ink.doa.workbench.security.identity.AuthApplicationService
import ink.doa.workbench.security.identity.BootstrapAccountSupport
import ink.doa.workbench.security.identity.BootstrapAdminSupport
import ink.doa.workbench.security.identity.InstanceBootstrapService
import ink.doa.workbench.security.identity.LoginCompletionService
import ink.doa.workbench.security.identity.SessionService
import ink.doa.workbench.security.identity.auth.AuthCredentialSupport
import ink.doa.workbench.security.identity.auth.AuthenticationService
import ink.doa.workbench.security.identity.auth.BearerCredentialService
import ink.doa.workbench.security.identity.auth.CredentialCryptoSupport
import ink.doa.workbench.security.identity.auth.LoginOrchestrator
import ink.doa.workbench.security.identity.auth.PasswordLoginAuthenticator
import ink.doa.workbench.security.identity.auth.SecureRandomCredentialSecretGenerator
import ink.doa.workbench.security.identity.auth.SessionCredentialService
import ink.doa.workbench.security.identity.auth.Sha256CredentialHasher
import ink.doa.workbench.security.identity.auth.support.AuthIntegrationFixtures
import ink.doa.workbench.service.instance.support.UnusedPublicIdResolverDependencies
import ink.doa.workbench.tenant.instance.InstanceProperties
import ink.doa.workbench.testsupport.postgres.PostgresTestDatabaseLease
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

@Tags("integration")
class InstanceSetupApplicationServiceIntegrationTest :
  StringSpec({
    val postgresLease: PostgresTestDatabaseLease = AuthIntegrationFixtures.openSpecDatabase()
    lateinit var database: Database
    lateinit var service: InstanceSetupApplicationService

    beforeSpec {
      database = postgresLease.database
      val users = ExposedUserRepository(database)
      val loginMethods = ExposedLoginMethodRepository(database)
      val loginAccounts = ExposedLoginAccountStore(database)
      val userLoginAccounts = ExposedUserLoginAccountRepository(database)
      val loginDiscovery =
        ExposedLoginDiscoveryRepository(database, loginAccounts, userLoginAccounts)
      val passwordHasher =
        object : PasswordHasher {
          private val encoder = BCryptPasswordEncoder()

          override fun hash(rawPassword: String): String =
            encoder.encode(rawPassword) ?: error("Password encoding failed.")
        }
      val tenants = ExposedTenantRepository(database)
      val deps = UnusedPublicIdResolverDependencies(loginMethods = loginMethods)
      val clock = Clock.fixed(Instant.parse("2026-07-03T00:00:00Z"), ZoneOffset.UTC)
      val adminUserCommands = ExposedAdminUserCommandRepository(database)
      val adminUserQueries = ExposedAdminUserQueryRepository(database)
      val accessGrants = ExposedAccessGrantRepository(database)
      val authEvents = ExposedAuthEventRepository(database)
      val sessions = ExposedAuthSessionRepository(database)
      val bearerTokens = ExposedBearerTokenRepository(database)
      val tenantMembers = ExposedTenantMemberRepository(database)
      val secretGenerator = SecureRandomCredentialSecretGenerator()
      val credentialHasher = Sha256CredentialHasher()
      val credentials = AuthCredentialSupport(users, loginAccounts, authEvents)
      val crypto = CredentialCryptoSupport(secretGenerator, credentialHasher)
      val sessionCredentialService =
        SessionCredentialService(
          credentials = credentials,
          crypto = crypto,
          sessions = sessions,
          clock = clock,
        )
      val bearerCredentialService =
        BearerCredentialService(
          credentials = credentials,
          crypto = crypto,
          bearerTokens = bearerTokens,
          clock = clock,
        )
      val authenticationService =
        AuthenticationService(
          loginAccounts = loginAccounts,
          authEvents = authEvents,
          loginOrchestrator =
            LoginOrchestrator(
              listOf(
                PasswordLoginAuthenticator(
                  loginMethods = loginMethods,
                  loginAccounts = loginAccounts,
                  userLoginAccounts = userLoginAccounts,
                  passwordVerifier = passwordVerifier(encoder = BCryptPasswordEncoder()),
                )
              )
            ),
          sessionCredentialService = sessionCredentialService,
          bearerCredentialService = bearerCredentialService,
          clock = clock,
        )
      val publicIds =
        PublicIdResolver(
          identity = PublicIdIdentitySupport(tenants, users, loginMethods, bearerTokens),
          permission = PublicIdPermissionSupport(adminUserQueries, accessGrants),
          projects = deps.projects,
        )
      val loginCompletionService =
        LoginCompletionService(
          loginMethods = loginMethods,
          loginDiscovery = loginDiscovery,
          tenantMembers = tenantMembers,
          tenants = tenants,
          adminUserQueries = adminUserQueries,
          clock = clock,
        )
      val authApplicationService =
        AuthApplicationService(
          authenticationService = authenticationService,
          sessionService =
            SessionService(
              sessions = sessions,
              tenantMembers = tenantMembers,
              tenants = tenants,
              publicIds = publicIds,
              loginCompletionService = loginCompletionService,
              clock = clock,
            ),
          loginCompletionService = loginCompletionService,
          bearerCredentialService = bearerCredentialService,
          publicIds = publicIds,
        )
      service =
        InstanceSetupApplicationService(
          instanceBootstrapService =
            InstanceBootstrapService(
              accounts =
                BootstrapAccountSupport(
                  users = users,
                  loginMethods = loginMethods,
                  loginAccounts = loginAccounts,
                  userLoginAccounts = userLoginAccounts,
                  passwordHasher = passwordHasher,
                ),
              admin =
                BootstrapAdminSupport(
                  adminUserCommands = adminUserCommands,
                  adminUserQueries = adminUserQueries,
                  accessGrants = accessGrants,
                ),
              clock = clock,
            ),
          instanceProperties = InstanceProperties(setupToken = null, id = null, name = null),
          authApplicationService = authApplicationService,
        )
    }

    afterSpec {
      postgresLease.close()
    }

    "bootstrap creates system administrator and session" {
      runBlocking {
        service.setupStatus().initialized shouldBe false
        val result =
          service.bootstrap(
            BootstrapInstanceAdminCommand(
              displayName = "Admin",
              email = "admin@example.test",
              password = "secure-password-1",
            )
          )
        result.user.displayName shouldBe "Admin"
        result.loginMethod.code shouldBe "instance_password"
        result.session.sessionSecret.shouldNotBeBlank()
        service.setupStatus().initialized shouldBe true
      }
    }

    "bootstrap rejects second initialization" {
      shouldThrow<ink.doa.workbench.core.common.errors.InstanceAlreadyInitializedException> {
        runBlocking {
          service.bootstrap(
            BootstrapInstanceAdminCommand(
              displayName = "Other",
              email = "other@example.test",
              password = "secure-password-2",
            )
          )
        }
      }
    }
  })

private fun passwordVerifier(
  encoder: org.springframework.security.crypto.password.PasswordEncoder
) =
  object : ink.doa.workbench.core.identity.auth.PasswordVerifier {
    override fun verify(rawPassword: String, passwordHash: String): Boolean =
      encoder.matches(rawPassword, passwordHash)
  }
