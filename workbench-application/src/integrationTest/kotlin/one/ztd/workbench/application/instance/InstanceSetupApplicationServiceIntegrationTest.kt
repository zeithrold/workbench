package one.ztd.workbench.application.instance

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import one.ztd.workbench.application.identity.InstanceBootstrapService
import one.ztd.workbench.application.identity.PublicIdResolver
import one.ztd.workbench.application.instance.support.UnusedPublicIdResolverDependencies
import one.ztd.workbench.data.repository.identity.ExposedAuthEventRepository
import one.ztd.workbench.data.repository.identity.ExposedAuthSessionRepository
import one.ztd.workbench.data.repository.identity.ExposedBearerTokenRepository
import one.ztd.workbench.data.repository.identity.ExposedLoginAccountStore
import one.ztd.workbench.data.repository.identity.ExposedLoginDiscoveryRepository
import one.ztd.workbench.data.repository.identity.ExposedLoginMethodRepository
import one.ztd.workbench.data.repository.identity.ExposedTenantMemberRepository
import one.ztd.workbench.data.repository.identity.ExposedTenantRepository
import one.ztd.workbench.data.repository.identity.ExposedUserLoginAccountRepository
import one.ztd.workbench.data.repository.identity.ExposedUserRepository
import one.ztd.workbench.data.repository.permission.ExposedAccessGrantRepository
import one.ztd.workbench.data.repository.permission.ExposedAdminUserCommandRepository
import one.ztd.workbench.data.repository.permission.ExposedAdminUserQueryRepository
import one.ztd.workbench.identity.AuthApplicationService
import one.ztd.workbench.identity.BootstrapAccountSupport
import one.ztd.workbench.identity.BootstrapAdminSupport
import one.ztd.workbench.identity.LoginCompletionService
import one.ztd.workbench.identity.SessionService
import one.ztd.workbench.identity.auth.AuthCredentialSupport
import one.ztd.workbench.identity.auth.AuthenticationService
import one.ztd.workbench.identity.auth.BearerCredentialService
import one.ztd.workbench.identity.auth.CredentialCryptoSupport
import one.ztd.workbench.identity.auth.LoginOrchestrator
import one.ztd.workbench.identity.auth.PasswordHasher
import one.ztd.workbench.identity.auth.SecureRandomCredentialSecretGenerator
import one.ztd.workbench.identity.auth.SessionCredentialService
import one.ztd.workbench.identity.auth.Sha256CredentialHasher
import one.ztd.workbench.identity.common.IdentityPublicIdResolver
import one.ztd.workbench.identity.common.PublicIdIdentitySupport
import one.ztd.workbench.identity.common.PublicIdPermissionSupport
import one.ztd.workbench.identity.model.BootstrapInstanceAdminCommand
import one.ztd.workbench.security.identity.auth.PasswordLoginAuthenticator
import one.ztd.workbench.security.identity.auth.support.AuthIntegrationFixtures
import one.ztd.workbench.tenant.instance.InstanceProperties
import one.ztd.workbench.testsupport.postgres.PostgresTestDatabaseLease
import org.jetbrains.exposed.v1.jdbc.Database
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

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
      val identityPublicIds = IdentityPublicIdResolver(tenants, bearerTokens)
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
              publicIds = identityPublicIds,
              loginCompletionService = loginCompletionService,
              clock = clock,
            ),
          loginCompletionService = loginCompletionService,
          bearerCredentialService = bearerCredentialService,
          publicIds = identityPublicIds,
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
      shouldThrow<one.ztd.workbench.kernel.common.errors.InstanceAlreadyInitializedException> {
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
  object : one.ztd.workbench.identity.auth.PasswordVerifier {
    override fun verify(rawPassword: String, passwordHash: String): Boolean =
      encoder.matches(rawPassword, passwordHash)
  }
