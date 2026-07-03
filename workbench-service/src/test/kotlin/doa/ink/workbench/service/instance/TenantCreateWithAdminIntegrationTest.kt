package doa.ink.workbench.service.instance

import doa.ink.workbench.core.common.context.RequestHost
import doa.ink.workbench.core.common.errors.ResourceConflictException
import doa.ink.workbench.core.identity.auth.PasswordHasher
import doa.ink.workbench.core.identity.model.AcceptInvitationCommand
import doa.ink.workbench.core.identity.model.CreateTenantWithAdminCommand
import doa.ink.workbench.core.identity.model.InvitationType
import doa.ink.workbench.core.identity.model.TenantAdminAssignment
import doa.ink.workbench.core.identity.model.TenantStatus
import doa.ink.workbench.data.identity.ExposedInvitationRepository
import doa.ink.workbench.data.identity.ExposedLoginAccountStore
import doa.ink.workbench.data.identity.ExposedLoginMethodRepository
import doa.ink.workbench.data.identity.ExposedTenantLoginMethodSettingRepository
import doa.ink.workbench.data.identity.ExposedTenantMemberRepository
import doa.ink.workbench.data.identity.ExposedTenantRepository
import doa.ink.workbench.data.identity.ExposedUserLoginAccountRepository
import doa.ink.workbench.data.identity.ExposedUserRepository
import doa.ink.workbench.data.permission.ExposedAccessGrantRepository
import doa.ink.workbench.data.permission.ExposedAdminUserCommandRepository
import doa.ink.workbench.data.permission.ExposedAdminUserQueryRepository
import doa.ink.workbench.security.common.PublicIdResolver
import doa.ink.workbench.security.identity.auth.SecureRandomCredentialSecretGenerator
import doa.ink.workbench.security.identity.auth.Sha256CredentialHasher
import doa.ink.workbench.security.identity.auth.support.AuthIntegrationFixtures
import doa.ink.workbench.security.invitation.InvitationLinkBuilder
import doa.ink.workbench.security.invitation.InvitationLinkProperties
import doa.ink.workbench.security.invitation.InvitationService
import doa.ink.workbench.security.permission.AdminUserService
import doa.ink.workbench.service.instance.support.UnusedPublicIdResolverDependencies
import doa.ink.workbench.service.messaging.support.RecordingDomainEventPublisher
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.Tag
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.testcontainers.containers.PostgreSQLContainer

@Tag("integration")
class TenantCreateWithAdminIntegrationTest :
  StringSpec({
    val postgres: PostgreSQLContainer<*> = AuthIntegrationFixtures.startPostgres()
    lateinit var database: Database
    lateinit var tenantService: TenantManagementService
    lateinit var invitationService: InvitationService
    lateinit var users: ExposedUserRepository
    lateinit var tenantMembers: ExposedTenantMemberRepository
    lateinit var adminUserQueries: ExposedAdminUserQueryRepository
    lateinit var accessGrants: ExposedAccessGrantRepository
    lateinit var instanceAdminUserId: UUID

    beforeSpec {
      database = AuthIntegrationFixtures.connectDatabase(postgres)
      users = ExposedUserRepository(database)
      val loginMethods = ExposedLoginMethodRepository(database)
      val loginAccounts = ExposedLoginAccountStore(database)
      val userLoginAccounts = ExposedUserLoginAccountRepository(database)
      val tenantLoginSettings = ExposedTenantLoginMethodSettingRepository(database)
      val tenants = ExposedTenantRepository(database)
      tenantMembers = ExposedTenantMemberRepository(database)
      adminUserQueries = ExposedAdminUserQueryRepository(database)
      val adminUserCommands = ExposedAdminUserCommandRepository(database)
      accessGrants = ExposedAccessGrantRepository(database)
      val deps = UnusedPublicIdResolverDependencies(loginMethods = loginMethods)
      val clock = Clock.fixed(Instant.parse("2026-07-03T00:00:00Z"), ZoneOffset.UTC)
      val publicIds =
        PublicIdResolver(
          tenants = tenants,
          users = users,
          loginMethods = loginMethods,
          bearerTokens = deps.bearerTokens,
          adminUserQueries = adminUserQueries,
          accessGrants = accessGrants,
          projects = deps.projects,
        )
      val passwordHasher =
        object : PasswordHasher {
          private val encoder = BCryptPasswordEncoder()

          override fun hash(rawPassword: String): String =
            encoder.encode(rawPassword) ?: error("Password encoding failed.")
        }
      val adminUserService =
        AdminUserService(
          adminUserCommands = adminUserCommands,
          adminUserQueries = adminUserQueries,
          accessGrants = accessGrants,
          userRepository = users,
          tenantMembers = tenantMembers,
          publicIds = publicIds,
          clock = clock,
        )
      invitationService =
        InvitationService(
          invitations = ExposedInvitationRepository(database),
          tenants = tenants,
          users = users,
          loginMethods = loginMethods,
          loginAccounts = loginAccounts,
          userLoginAccounts = userLoginAccounts,
          adminUserService = adminUserService,
          credentialHasher = Sha256CredentialHasher(),
          secretGenerator = SecureRandomCredentialSecretGenerator(),
          passwordHasher = passwordHasher,
          invitationLinkBuilder =
            InvitationLinkBuilder(
              InvitationLinkProperties(publicBaseUrl = "https://app.example.test")
            ),
          clock = clock,
        )
      tenantService =
        TenantManagementService(
          tenants = tenants,
          users = users,
          loginMethods = loginMethods,
          tenantLoginSettings = tenantLoginSettings,
          publicIds = publicIds,
          adminUserService = adminUserService,
          invitationService = invitationService,
          domainEventPublisher = RecordingDomainEventPublisher(),
          clock = clock,
        )

      runBlocking {
        val bootstrapUser =
          users.create(
            doa.ink.workbench.core.identity.model.CreateUserCommand(
              displayName = "Instance Admin",
              primaryEmail = "instance-admin@example.test",
            )
          )
        instanceAdminUserId = bootstrapUser.id
      }
    }

    afterSpec {
      postgres.stop()
    }

    "create tenant with SELF admin provisions membership grants and password login" {
      runBlocking {
        val slug = "self-${UUID.randomUUID().toString().take(8)}"
        val result =
          tenantService.createWithAdmin(
            command =
              CreateTenantWithAdminCommand(
                name = "Self Tenant",
                slug = slug,
                adminAssignment = TenantAdminAssignment.SelfAssignment,
              ),
            actorUserId = instanceAdminUserId,
            requestHost = RequestHost("https", "app.example.test"),
          )
        result.tenant.status shouldBe TenantStatus.ACTIVE
        result.admin.shouldNotBeNull()
        result.invitationLink shouldBe null
        tenantMembers.findByTenantAndUser(result.tenant.id, instanceAdminUserId).shouldNotBeNull()
        accessGrants.listByTenant(result.tenant.id) shouldHaveSize 7
      }
    }

    "create tenant with USER admin assigns target user" {
      runBlocking {
        val target =
          users.create(
            doa.ink.workbench.core.identity.model.CreateUserCommand(
              displayName = "Target Admin",
              primaryEmail = "target-admin-${UUID.randomUUID().toString().take(8)}@example.test",
            )
          )
        val slug = "user-${UUID.randomUUID().toString().take(8)}"
        val result =
          tenantService.createWithAdmin(
            command =
              CreateTenantWithAdminCommand(
                name = "User Tenant",
                slug = slug,
                adminAssignment = TenantAdminAssignment.UserAssignment(target.id),
              ),
            actorUserId = instanceAdminUserId,
            requestHost = null,
          )
        result.admin?.userId shouldBe target.apiId.value
        adminUserQueries.listTenantAdmins(result.tenant.id).single().userId shouldBe target.id
      }
    }

    "create tenant with EMAIL_INVITE returns invitation link and pending tenant" {
      runBlocking {
        val slug = "invite-${UUID.randomUUID().toString().take(8)}"
        val result =
          tenantService.createWithAdmin(
            command =
              CreateTenantWithAdminCommand(
                name = "Invite Tenant",
                slug = slug,
                adminAssignment =
                  TenantAdminAssignment.EmailInviteAssignment(
                    email = "external-${UUID.randomUUID().toString().take(8)}@example.test",
                    displayName = "External Admin",
                  ),
              ),
            actorUserId = instanceAdminUserId,
            requestHost = RequestHost("https", "app.example.test"),
          )
        result.tenant.status shouldBe TenantStatus.PENDING_ACTIVATION
        result.admin shouldBe null
        result.invitationLink.shouldNotBeNull().shouldContain("/invitations/")
      }
    }

    "accept tenant admin invitation activates tenant and provisions admin" {
      runBlocking {
        val email = "accept-${UUID.randomUUID().toString().take(8)}@example.test"
        val slug = "accept-${UUID.randomUUID().toString().take(8)}"
        val created =
          tenantService.createWithAdmin(
            command =
              CreateTenantWithAdminCommand(
                name = "Accept Tenant",
                slug = slug,
                adminAssignment =
                  TenantAdminAssignment.EmailInviteAssignment(
                    email = email,
                    displayName = "Invitee",
                  ),
              ),
            actorUserId = instanceAdminUserId,
            requestHost = RequestHost("https", "app.example.test"),
          )
        val token = created.invitationLink.shouldNotBeNull().substringAfterLast('/')
        val accepted =
          invitationService.accept(
            AcceptInvitationCommand(
              token = token,
              displayName = "Invitee",
              password = "secure-password-1",
            )
          )
        accepted.type shouldBe InvitationType.TENANT_ADMIN
        accepted.tenant.slug shouldBe slug
        val activated = tenantService.get(created.tenant.apiId.value)
        activated.status shouldBe TenantStatus.ACTIVE
        accessGrants.listByTenant(created.tenant.id) shouldHaveSize 7
      }
    }

    "accept rejects when email already exists" {
      runBlocking {
        val email = "conflict-${UUID.randomUUID().toString().take(8)}@example.test"
        users.create(
          doa.ink.workbench.core.identity.model.CreateUserCommand(
            displayName = "Existing",
            primaryEmail = email,
          )
        )
        val slug = "conflict-${UUID.randomUUID().toString().take(8)}"
        val created =
          tenantService.createWithAdmin(
            command =
              CreateTenantWithAdminCommand(
                name = "Conflict Tenant",
                slug = slug,
                adminAssignment =
                  TenantAdminAssignment.EmailInviteAssignment(email = email, displayName = null),
              ),
            actorUserId = instanceAdminUserId,
            requestHost = RequestHost("https", "app.example.test"),
          )
        val token = created.invitationLink.shouldNotBeNull().substringAfterLast('/')
        shouldThrow<ResourceConflictException> {
          invitationService.accept(
            AcceptInvitationCommand(
              token = token,
              displayName = "Invitee",
              password = "secure-password-1",
            )
          )
        }
      }
    }
  })
