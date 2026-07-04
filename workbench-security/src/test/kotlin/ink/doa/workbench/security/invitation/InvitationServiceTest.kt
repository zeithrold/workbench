package ink.doa.workbench.security.invitation

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.InvitationRepository
import ink.doa.workbench.core.identity.LoginAccountStore
import ink.doa.workbench.core.identity.LoginMethodRepository
import ink.doa.workbench.core.identity.TenantRepository
import ink.doa.workbench.core.identity.UserLoginAccountRepository
import ink.doa.workbench.core.identity.UserRepository
import ink.doa.workbench.core.identity.auth.CredentialHasher
import ink.doa.workbench.core.identity.auth.CredentialSecretGenerator
import ink.doa.workbench.core.identity.auth.PasswordHasher
import ink.doa.workbench.core.identity.model.AcceptInvitationCommand
import ink.doa.workbench.core.identity.model.InvitationRecord
import ink.doa.workbench.core.identity.model.InvitationType
import ink.doa.workbench.core.identity.model.TenantRecord
import ink.doa.workbench.core.identity.model.TenantStatus
import ink.doa.workbench.security.permission.AdminUserService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking

class InvitationServiceTest :
  StringSpec({
    val invitations = mockk<InvitationRepository>()
    val tenants = mockk<TenantRepository>()
    val users = mockk<UserRepository>()
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
        invitations,
        tenants,
        users,
        loginMethods,
        loginAccounts,
        userLoginAccounts,
        adminUserService,
        credentialHasher,
        secretGenerator,
        passwordHasher,
        invitationLinkBuilder,
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
          type = InvitationType.TENANT_ADMIN,
          tenantId = tenantId,
          email = "admin@example.test",
          displayName = "Admin",
          invitedBy = invitedBy,
          requestHost = null,
        )
      }

      result.token shouldBe "invite-token"
      result.invitationLink shouldBe "https://workbench.test/invite/invite-token"
    }

    "accept rejects unsupported tenant member invitations" {
      val invitation = sampleInvitation(type = InvitationType.TENANT_MEMBER)
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
        .errorCode shouldBe WorkbenchErrorCode.TENANT_MEMBER_INVITATION_UNSUPPORTED
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
