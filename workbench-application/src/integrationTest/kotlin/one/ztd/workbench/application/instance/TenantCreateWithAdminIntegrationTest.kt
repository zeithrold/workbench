package one.ztd.workbench.application.instance

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
import one.ztd.workbench.application.identity.PublicIdResolver
import one.ztd.workbench.application.instance.support.UnusedPublicIdResolverDependencies
import one.ztd.workbench.application.invitation.InvitationCollaborators
import one.ztd.workbench.application.invitation.InvitationIdentitySupport
import one.ztd.workbench.application.invitation.InvitationService
import one.ztd.workbench.application.permission.AdminUserPersistenceSupport
import one.ztd.workbench.application.permission.AdminUserService
import one.ztd.workbench.data.repository.identity.ExposedInvitationRepository
import one.ztd.workbench.data.repository.identity.ExposedLoginAccountStore
import one.ztd.workbench.data.repository.identity.ExposedLoginMethodRepository
import one.ztd.workbench.data.repository.identity.ExposedTenantLoginMethodSettingRepository
import one.ztd.workbench.data.repository.identity.ExposedTenantMemberRepository
import one.ztd.workbench.data.repository.identity.ExposedTenantRepository
import one.ztd.workbench.data.repository.identity.ExposedUserLoginAccountRepository
import one.ztd.workbench.data.repository.identity.ExposedUserRepository
import one.ztd.workbench.data.repository.permission.ExposedAccessGrantRepository
import one.ztd.workbench.data.repository.permission.ExposedAdminUserCommandRepository
import one.ztd.workbench.data.repository.permission.ExposedAdminUserQueryRepository
import one.ztd.workbench.identity.TenantLoginMethodService
import one.ztd.workbench.identity.UserLookupService
import one.ztd.workbench.identity.auth.PasswordHasher
import one.ztd.workbench.identity.auth.SecureRandomCredentialSecretGenerator
import one.ztd.workbench.identity.auth.Sha256CredentialHasher
import one.ztd.workbench.identity.common.PublicIdIdentitySupport
import one.ztd.workbench.identity.common.PublicIdPermissionSupport
import one.ztd.workbench.identity.invitation.InvitationLinkBuilder
import one.ztd.workbench.identity.invitation.InvitationLinkProperties
import one.ztd.workbench.identity.model.AcceptInvitationCommand
import one.ztd.workbench.identity.model.CreateTenantWithAdminCommand
import one.ztd.workbench.identity.model.InvitationType
import one.ztd.workbench.identity.model.TenantAdminAssignment
import one.ztd.workbench.kernel.common.context.RequestHost
import one.ztd.workbench.kernel.common.errors.ResourceConflictException
import one.ztd.workbench.security.identity.auth.support.AuthIntegrationFixtures
import one.ztd.workbench.tenant.model.TenantStatus
import one.ztd.workbench.tenant.tenant.TenantService
import one.ztd.workbench.testsupport.postgres.PostgresTestDatabaseLease
import org.jetbrains.exposed.v1.jdbc.Database
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class TenantCreateWithAdminIntegrationTest :
  StringSpec({
    val postgresLease: PostgresTestDatabaseLease = AuthIntegrationFixtures.openSpecDatabase()
    lateinit var database: Database
    lateinit var tenantService: TenantManagementApplicationService
    lateinit var invitationService: InvitationService
    lateinit var users: ExposedUserRepository
    lateinit var tenantMembers: ExposedTenantMemberRepository
    lateinit var adminUserQueries: ExposedAdminUserQueryRepository
    lateinit var accessGrants: ExposedAccessGrantRepository
    lateinit var instanceAdminUserId: UUID

    beforeSpec {
      database = postgresLease.database
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
          identity = PublicIdIdentitySupport(tenants, users, loginMethods, deps.bearerTokens),
          permission = PublicIdPermissionSupport(adminUserQueries, accessGrants),
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
          persistence =
            AdminUserPersistenceSupport(
              adminUserCommands = adminUserCommands,
              adminUserQueries = adminUserQueries,
              accessGrants = accessGrants,
              userRepository = users,
              tenantMembers = tenantMembers,
            ),
          publicIds = publicIds,
          clock = clock,
        )
      invitationService =
        InvitationService(
          identity =
            InvitationIdentitySupport(
              tenants = tenants,
              tenantMembers = tenantMembers,
              users = users,
              login =
                one.ztd.workbench.application.invitation.InvitationLoginSupport(
                  loginMethods,
                  loginAccounts,
                  userLoginAccounts,
                  passwordHasher,
                ),
            ),
          collaborators =
            InvitationCollaborators(
              invitations = ExposedInvitationRepository(database),
              credentialHasher = Sha256CredentialHasher(),
              secretGenerator = SecureRandomCredentialSecretGenerator(),
              invitationLinkBuilder =
                InvitationLinkBuilder(
                  InvitationLinkProperties(publicBaseUrl = "https://app.example.test")
                ),
              adminUserService = adminUserService,
            ),
          clock = clock,
        )
      tenantService =
        TenantManagementApplicationService(
          dependencies =
            TenantManagementDependencies(
              tenants = TenantService(tenants),
              identity =
                TenantIdentityDependencies(
                  tenantLoginMethods = TenantLoginMethodService(loginMethods, tenantLoginSettings),
                  userLookupService = UserLookupService(users),
                  adminUserService = adminUserService,
                  invitationService = invitationService,
                ),
              defaultWorkItemTemplate = io.mockk.mockk(relaxed = true),
              clock = clock,
            )
        )

      runBlocking {
        val bootstrapUser =
          users.create(
            one.ztd.workbench.identity.model.CreateUserCommand(
              displayName = "Instance Admin",
              primaryEmail = "instance-admin@example.test",
            )
          )
        instanceAdminUserId = bootstrapUser.id
      }
    }

    afterSpec {
      postgresLease.close()
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
            one.ztd.workbench.identity.model.CreateUserCommand(
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
          one.ztd.workbench.identity.model.CreateUserCommand(
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
