package one.ztd.workbench.application.invitation

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
import one.ztd.workbench.application.permission.AdminUserService
import one.ztd.workbench.application.permission.AdminUserView
import one.ztd.workbench.identity.InvitationRepository
import one.ztd.workbench.identity.LoginAccountStore
import one.ztd.workbench.identity.LoginMethodRepository
import one.ztd.workbench.identity.TenantMemberRepository
import one.ztd.workbench.identity.UserLoginAccountRepository
import one.ztd.workbench.identity.UserRepository
import one.ztd.workbench.identity.auth.CredentialHasher
import one.ztd.workbench.identity.auth.CredentialSecretGenerator
import one.ztd.workbench.identity.auth.PasswordHasher
import one.ztd.workbench.identity.invitation.InvitationLinkBuilder
import one.ztd.workbench.identity.model.AcceptInvitationCommand
import one.ztd.workbench.identity.model.InvitationRecord
import one.ztd.workbench.identity.model.InvitationType
import one.ztd.workbench.identity.model.LoginAccountParameterKey
import one.ztd.workbench.identity.model.LoginAccountParameterRecord
import one.ztd.workbench.identity.model.LoginAccountRecord
import one.ztd.workbench.identity.model.LoginMethodDefinitionRecord
import one.ztd.workbench.identity.model.LoginMethodKind
import one.ztd.workbench.identity.model.UserLoginAccountRecord
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.ResourceConflictException
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.tenant.TenantRepository
import one.ztd.workbench.tenant.model.TenantRecord
import one.ztd.workbench.tenant.model.TenantStatus

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
          InvitationLoginSupport(loginMethods, loginAccounts, userLoginAccounts, passwordHasher),
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
          scope = one.ztd.workbench.identity.permission.AdminScope.TENANT,
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
