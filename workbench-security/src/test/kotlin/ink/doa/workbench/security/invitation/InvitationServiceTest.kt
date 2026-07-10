package ink.doa.workbench.security.invitation

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.ResourceConflictException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.InvitationRepository
import ink.doa.workbench.core.identity.LoginAccountStore
import ink.doa.workbench.core.identity.LoginMethodRepository
import ink.doa.workbench.core.identity.TenantMemberRepository
import ink.doa.workbench.core.identity.TenantRepository
import ink.doa.workbench.core.identity.UserLoginAccountRepository
import ink.doa.workbench.core.identity.UserRepository
import ink.doa.workbench.core.identity.auth.CredentialHasher
import ink.doa.workbench.core.identity.auth.CredentialSecretGenerator
import ink.doa.workbench.core.identity.auth.PasswordHasher
import ink.doa.workbench.core.identity.model.AcceptInvitationCommand
import ink.doa.workbench.core.identity.model.InvitationRecord
import ink.doa.workbench.core.identity.model.InvitationType
import ink.doa.workbench.core.identity.model.LoginAccountParameterKey
import ink.doa.workbench.core.identity.model.LoginAccountParameterRecord
import ink.doa.workbench.core.identity.model.LoginAccountRecord
import ink.doa.workbench.core.identity.model.LoginMethodDefinitionRecord
import ink.doa.workbench.core.identity.model.LoginMethodKind
import ink.doa.workbench.core.identity.model.TenantRecord
import ink.doa.workbench.core.identity.model.TenantStatus
import ink.doa.workbench.core.identity.model.UserLoginAccountRecord
import ink.doa.workbench.core.identity.model.UserRecord
import ink.doa.workbench.security.permission.AdminUserService
import ink.doa.workbench.security.permission.AdminUserView
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

class InvitationServiceTest :
  StringSpec({
    val invitations = mockk<InvitationRepository>()
    val tenants = mockk<TenantRepository>()
    val users = mockk<UserRepository>()
    val tenantMembers = mockk<TenantMemberRepository>()
    val loginMethods = mockk<LoginMethodRepository>()
    val loginAccounts = mockk<LoginAccountStore>()
    val userLoginAccounts = mockk<UserLoginAccountRepository>()
    val adminUserService = mockk<AdminUserService>()
    val credentialHasher = mockk<CredentialHasher>()
    val secretGenerator = mockk<CredentialSecretGenerator>()
    val passwordHasher = mockk<PasswordHasher>()
    val invitationLinkBuilder = mockk<InvitationLinkBuilder>()
    val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
    val service =
      InvitationService(
        InvitationIdentitySupport(
          tenants,
          tenantMembers,
          users,
          loginMethods,
          loginAccounts,
          userLoginAccounts,
          passwordHasher,
        ),
        InvitationCollaborators(
          invitations,
          credentialHasher,
          secretGenerator,
          invitationLinkBuilder,
          adminUserService,
        ),
        clock,
      )

    "create returns token and invitation link" {
      val tenantId = UUID.randomUUID()
      val invitedBy = UUID.randomUUID()
      coEvery { secretGenerator.generate() } returns "invite-token"
      coEvery { credentialHasher.hash("invite-token") } returns "hash"
      coEvery { invitations.create(any()) } returns
        sampleInvitation(type = InvitationType.TENANT_ADMIN)
      coEvery { invitationLinkBuilder.buildInvitationLink("invite-token", null) } returns
        "https://workbench.test/invite/invite-token"

      val result = runBlocking {
        service.create(
          CreateManagedInvitationCommand(
            type = InvitationType.TENANT_ADMIN,
            tenantId = tenantId,
            email = "admin@example.test",
            displayName = "Admin",
            invitedBy = invitedBy,
            requestHost = null,
          )
        )
      }

      result.token shouldBe "invite-token"
      result.invitationLink shouldBe "https://workbench.test/invite/invite-token"
    }

    "accept requires authenticated claim when a tenant member invite matches an existing user" {
      val invitation = sampleInvitation(type = InvitationType.TENANT_MEMBER)
      coEvery { users.findByPrimaryEmail(invitation.normalizedEmail) } returns
        UserRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("usr"),
          displayName = "Existing user",
          primaryEmail = invitation.normalizedEmail,
        )
      coEvery { invitations.findActiveByHash("hash", any()) } returns invitation
      coEvery { credentialHasher.hash("token") } returns "hash"

      shouldThrow<InvalidRequestException> {
          runBlocking {
            service.accept(
              AcceptInvitationCommand(
                token = "token",
                displayName = "Admin",
                password = "secret",
              )
            )
          }
        }
        .errorCode shouldBe
        WorkbenchErrorCode.TENANT_MEMBER_INVITATION_AUTHENTICATED_ACCEPTANCE_REQUIRED
    }

    "acceptExisting activates tenant membership for matching invited user" {
      val invitation = sampleInvitation(type = InvitationType.TENANT_MEMBER)
      val tenant = sampleTenant(invitation.tenantId).copy(status = TenantStatus.ACTIVE)
      val user =
        UserRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("usr"),
          displayName = "Existing user",
          primaryEmail = invitation.normalizedEmail,
        )
      coEvery { invitations.findActiveByHash("hash", any()) } returns invitation
      coEvery { credentialHasher.hash("token") } returns "hash"
      coEvery { tenants.findById(invitation.tenantId) } returns tenant
      coEvery { tenantMembers.findByTenantAndUser(tenant.id, user.id) } returns null
      coEvery { tenantMembers.create(any()) } returns mockk(relaxed = true)
      coEvery { invitations.consume(invitation.id, any()) } returns true

      val accepted = runBlocking { service.acceptExisting("token", user) }

      accepted.type shouldBe InvitationType.TENANT_MEMBER
      accepted.user.id shouldBe user.apiId
      coVerify { tenantMembers.create(match { it.tenantId == tenant.id && it.userId == user.id }) }
      coVerify { invitations.consume(invitation.id, any()) }
    }

    "acceptExisting rejects a user whose email does not match the invitation" {
      val invitation = sampleInvitation(type = InvitationType.TENANT_MEMBER)
      val user =
        UserRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("usr"),
          displayName = "Other user",
          primaryEmail = "other@example.test",
        )
      coEvery { invitations.findActiveByHash("hash", any()) } returns invitation
      coEvery { credentialHasher.hash("token") } returns "hash"

      shouldThrow<InvalidRequestException> { runBlocking { service.acceptExisting("token", user) } }
        .errorCode shouldBe WorkbenchErrorCode.TENANT_MEMBER_INVITATION_EMAIL_MISMATCH
    }

    "preview returns tenant and email for active invitation" {
      val invitation = sampleInvitation(type = InvitationType.TENANT_ADMIN)
      val tenant = sampleTenant(invitation.tenantId)
      coEvery { invitations.findActiveByHash("hash", any()) } returns invitation
      coEvery { credentialHasher.hash("token") } returns "hash"
      coEvery { tenants.findById(invitation.tenantId) } returns tenant

      val preview = runBlocking { service.preview("token") }

      preview.email shouldBe invitation.email
      preview.tenant.name shouldBe tenant.name
    }

    "preview rejects invalid or expired invitations" {
      coEvery { credentialHasher.hash("bad-token") } returns "hash"
      coEvery { invitations.findActiveByHash("hash", any()) } returns null

      shouldThrow<InvalidRequestException> { runBlocking { service.preview("bad-token") } }
        .errorCode shouldBe WorkbenchErrorCode.INVITATION_INVALID_OR_EXPIRED
    }

    "preview throws when tenant is missing" {
      val invitation = sampleInvitation(type = InvitationType.TENANT_ADMIN)
      coEvery { invitations.findActiveByHash("hash", any()) } returns invitation
      coEvery { credentialHasher.hash("token") } returns "hash"
      coEvery { tenants.findById(invitation.tenantId) } returns null

      shouldThrow<ResourceNotFoundException> { runBlocking { service.preview("token") } }
        .errorCode shouldBe WorkbenchErrorCode.RESOURCE_TENANT_NOT_FOUND
    }

    "accept provisions tenant admin and activates tenant" {
      val invitation = sampleInvitation(type = InvitationType.TENANT_ADMIN)
      val tenant = sampleTenant(invitation.tenantId)
      val passwordMethodId = UUID.randomUUID()
      val userId = UUID.randomUUID()
      val loginAccountId = UUID.randomUUID()
      val activatedTenant = tenant.copy(status = TenantStatus.ACTIVE)
      val user =
        UserRecord(
          id = userId,
          apiId = PublicId.new("usr"),
          displayName = "Admin",
          primaryEmail = invitation.normalizedEmail,
        )
      val loginAccount =
        LoginAccountRecord(
          id = loginAccountId,
          apiId = PublicId.new("lac"),
          loginMethodId = passwordMethodId,
          subject = invitation.email,
          normalizedSubject = invitation.normalizedEmail,
          displayName = "Admin",
          lastUsedAt = null,
          disabledAt = null,
          disabledBy = null,
          createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
          updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        )

      coEvery { invitations.findActiveByHash("hash", any()) } returns invitation
      coEvery { credentialHasher.hash("token") } returns "hash"
      coEvery { tenants.findById(invitation.tenantId) } returns tenant
      coEvery { users.findByPrimaryEmail(invitation.normalizedEmail) } returns null
      coEvery { loginMethods.findLoginMethodByCode("password") } returns
        LoginMethodDefinitionRecord(
          id = passwordMethodId,
          apiId = PublicId.new("lmd"),
          code = "password",
          kind = LoginMethodKind.PASSWORD,
          name = "Password",
          isBuiltin = true,
          isEnabledGlobally = true,
          createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
          updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        )
      coEvery { users.create(any()) } returns user
      coEvery { loginAccounts.createLoginAccount(any()) } returns loginAccount
      coEvery { passwordHasher.hash("secret") } returns "hashed-password"
      coEvery { loginAccounts.upsertParameter(any()) } returns
        LoginAccountParameterRecord(
          id = UUID.randomUUID(),
          loginAccountId = loginAccountId,
          parameterKey = LoginAccountParameterKey.PasswordHash,
          parameterValue = "hashed-password",
          secretRef = null,
          metadata = JsonObject(emptyMap()),
          createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
          updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        )
      coEvery { userLoginAccounts.linkUser(any()) } returns
        UserLoginAccountRecord(
          id = UUID.randomUUID(),
          userId = userId,
          loginAccountId = loginAccountId,
          linkedBy = userId,
          linkedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
          unlinkedAt = null,
        )
      coEvery {
        adminUserService.provisionTenantAdmin(tenant.id, user.id, invitation.invitedBy)
      } returns
        AdminUserView(
          id = PublicId.new("adm").value,
          userId = user.apiId.value,
          scope = ink.doa.workbench.core.permission.AdminScope.TENANT,
          tenantId = tenant.id.toString(),
          status = "active",
          validFrom = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
          validTo = null,
        )
      coEvery { tenants.update(any()) } returns activatedTenant
      coEvery { invitations.consume(invitation.id, any()) } returns true

      val accepted = runBlocking {
        service.accept(
          AcceptInvitationCommand(
            token = "token",
            displayName = "Admin",
            password = "secret",
          )
        )
      }

      accepted.type shouldBe InvitationType.TENANT_ADMIN
      accepted.tenant.name shouldBe activatedTenant.name
      accepted.user.displayName shouldBe "Admin"
      coVerify { adminUserService.provisionTenantAdmin(tenant.id, user.id, invitation.invitedBy) }
    }

    "accept rejects when tenant is not pending activation" {
      val invitation = sampleInvitation(type = InvitationType.TENANT_ADMIN)
      val tenant = sampleTenant(invitation.tenantId).copy(status = TenantStatus.ACTIVE)
      coEvery { invitations.findActiveByHash("hash", any()) } returns invitation
      coEvery { credentialHasher.hash("token") } returns "hash"
      coEvery { tenants.findById(invitation.tenantId) } returns tenant

      shouldThrow<InvalidRequestException> {
          runBlocking {
            service.accept(
              AcceptInvitationCommand(
                token = "token",
                displayName = "Admin",
                password = "secret",
              )
            )
          }
        }
        .errorCode shouldBe WorkbenchErrorCode.TENANT_PENDING_ACTIVATION_REQUIRED
    }

    "accept rejects when email already exists" {
      val invitation = sampleInvitation(type = InvitationType.TENANT_ADMIN)
      val tenant = sampleTenant(invitation.tenantId)
      coEvery { invitations.findActiveByHash("hash", any()) } returns invitation
      coEvery { credentialHasher.hash("token") } returns "hash"
      coEvery { tenants.findById(invitation.tenantId) } returns tenant
      coEvery { users.findByPrimaryEmail(invitation.normalizedEmail) } returns
        UserRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("usr"),
          displayName = "Existing",
          primaryEmail = invitation.normalizedEmail,
        )

      shouldThrow<ResourceConflictException> {
          runBlocking {
            service.accept(
              AcceptInvitationCommand(
                token = "token",
                displayName = "Admin",
                password = "secret",
              )
            )
          }
        }
        .errorCode shouldBe WorkbenchErrorCode.USER_EMAIL_ALREADY_EXISTS
    }
  })

private fun sampleTenant(id: UUID): TenantRecord =
  TenantRecord(
    id = id,
    apiId = PublicId.new("ten"),
    slug = "acme",
    name = "Acme",
    status = TenantStatus.PENDING_ACTIVATION,
    createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
    updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
  )

private fun sampleInvitation(type: InvitationType): InvitationRecord {
  val tenantId = UUID.randomUUID()
  return InvitationRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("inv"),
    type = type,
    tenantId = tenantId,
    email = "admin@example.test",
    normalizedEmail = "admin@example.test",
    displayName = "Admin",
    tokenHash = "hash",
    invitedBy = UUID.randomUUID(),
    expiresAt = OffsetDateTime.parse("2026-07-11T00:00:00Z"),
    consumedAt = null,
    createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
  )
}
