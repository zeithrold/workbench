package doa.ink.workbench.service.instance

import doa.ink.workbench.core.identity.auth.PasswordHasher
import doa.ink.workbench.core.identity.model.BootstrapInstanceAdminCommand
import doa.ink.workbench.data.identity.ExposedAuthEventRepository
import doa.ink.workbench.data.identity.ExposedAuthSessionRepository
import doa.ink.workbench.data.identity.ExposedBearerTokenRepository
import doa.ink.workbench.data.identity.ExposedLoginAccountRepository
import doa.ink.workbench.data.identity.ExposedTenantMemberRepository
import doa.ink.workbench.data.identity.ExposedTenantRepository
import doa.ink.workbench.data.identity.ExposedUserRepository
import doa.ink.workbench.data.permission.ExposedAccessGrantRepository
import doa.ink.workbench.data.permission.ExposedAdminUserRepository
import doa.ink.workbench.service.common.PublicIdResolver
import doa.ink.workbench.service.identity.AuthApplicationService
import doa.ink.workbench.service.identity.LoginCompletionService
import doa.ink.workbench.service.identity.LoginDiscoveryService
import doa.ink.workbench.service.identity.MembershipService
import doa.ink.workbench.service.identity.SessionService
import doa.ink.workbench.service.identity.auth.AuthenticationService
import doa.ink.workbench.service.identity.auth.FederatedAuthService
import doa.ink.workbench.service.identity.auth.LoginOrchestrator
import doa.ink.workbench.service.identity.auth.MagicLinkAuthService
import doa.ink.workbench.service.identity.auth.PasswordLoginAuthenticator
import doa.ink.workbench.service.identity.auth.SecureRandomCredentialSecretGenerator
import doa.ink.workbench.service.identity.auth.Sha256CredentialHasher
import doa.ink.workbench.service.identity.auth.support.AuthIntegrationFixtures
import doa.ink.workbench.service.instance.support.UnusedPublicIdResolverDependencies
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.Tag
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.testcontainers.containers.PostgreSQLContainer

@Tag("integration")
class InstanceSetupIntegrationTest :
  StringSpec({
    val postgres: PostgreSQLContainer<*> = AuthIntegrationFixtures.startPostgres()
    lateinit var database: Database
    lateinit var service: InstanceSetupService

    beforeSpec {
      database = AuthIntegrationFixtures.connectDatabase(postgres)
      val users = ExposedUserRepository(database)
      val loginAccounts = ExposedLoginAccountRepository(database)
      val passwordHasher =
        object : PasswordHasher {
          private val encoder = BCryptPasswordEncoder()

          override fun hash(rawPassword: String): String =
            encoder.encode(rawPassword) ?: error("Password encoding failed.")
        }
      val tenants = ExposedTenantRepository(database)
      val deps = UnusedPublicIdResolverDependencies(loginAccounts = loginAccounts)
      val clock = Clock.fixed(Instant.parse("2026-07-03T00:00:00Z"), ZoneOffset.UTC)
      val adminUsers = ExposedAdminUserRepository(database)
      val accessGrants = ExposedAccessGrantRepository(database)
      val authenticationService =
        AuthenticationService(
          users = users,
          loginAccounts = loginAccounts,
          authEvents = ExposedAuthEventRepository(database),
          sessions = ExposedAuthSessionRepository(database),
          bearerTokens = ExposedBearerTokenRepository(database),
          loginOrchestrator =
            LoginOrchestrator(
              listOf(
                PasswordLoginAuthenticator(
                  loginAccounts,
                  passwordVerifier(encoder = BCryptPasswordEncoder()),
                )
              )
            ),
          secretGenerator = SecureRandomCredentialSecretGenerator(),
          credentialHasher = Sha256CredentialHasher(),
          clock = clock,
        )
      val publicIds =
        PublicIdResolver(
          tenants = tenants,
          users = users,
          loginAccounts = loginAccounts,
          bearerTokens = ExposedBearerTokenRepository(database),
          adminUsers = adminUsers,
          accessGrants = accessGrants,
          projects = deps.projects,
        )
      val loginCompletionService =
        LoginCompletionService(
          loginAccounts = loginAccounts,
          tenantMembers = ExposedTenantMemberRepository(database),
          tenants = tenants,
          adminUsers = adminUsers,
          clock = clock,
        )
      val authApplicationService =
        AuthApplicationService(
          authenticationService = authenticationService,
          sessionService =
            SessionService(
              sessions = ExposedAuthSessionRepository(database),
              tenantMembers = ExposedTenantMemberRepository(database),
              tenants = tenants,
              publicIds = publicIds,
              loginCompletionService = loginCompletionService,
              clock = clock,
            ),
          membershipService =
            MembershipService(
              tenantMembers = ExposedTenantMemberRepository(database),
              tenants = tenants,
              adminUsers = adminUsers,
              clock = clock,
            ),
          loginAccounts = loginAccounts,
          loginDiscoveryService =
            LoginDiscoveryService(
              users = users,
              loginAccounts = loginAccounts,
              tenantMembers = ExposedTenantMemberRepository(database),
              adminUsers = adminUsers,
              clock = clock,
            ),
          loginCompletionService = loginCompletionService,
          federatedAuthService = mockk<FederatedAuthService>(relaxed = true),
          magicLinkAuthService = mockk<MagicLinkAuthService>(relaxed = true),
          publicIds = publicIds,
        )
      service =
        InstanceSetupService(
          users = users,
          loginAccounts = loginAccounts,
          adminUsers = adminUsers,
          accessGrants = accessGrants,
          passwordHasher = passwordHasher,
          instanceProperties = InstanceProperties(),
          authApplicationService = authApplicationService,
          clock = clock,
        )
    }

    afterSpec {
      postgres.stop()
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
      shouldThrow<doa.ink.workbench.core.common.errors.InstanceAlreadyInitializedException> {
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
  object : doa.ink.workbench.core.identity.auth.PasswordVerifier {
    override fun verify(rawPassword: String, passwordHash: String): Boolean =
      encoder.matches(rawPassword, passwordHash)
  }
