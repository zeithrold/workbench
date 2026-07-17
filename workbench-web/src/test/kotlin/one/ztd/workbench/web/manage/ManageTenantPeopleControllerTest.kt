package one.ztd.workbench.web.manage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import one.ztd.workbench.application.identity.TenantMemberManagementService
import one.ztd.workbench.application.identity.TenantMemberView
import one.ztd.workbench.application.invitation.InvitationService
import one.ztd.workbench.application.invitation.ManagedInvitationService
import one.ztd.workbench.application.invitation.ManagedInvitationView
import one.ztd.workbench.identity.common.summary.UserSummary
import one.ztd.workbench.identity.model.InvitationType
import one.ztd.workbench.identity.model.TenantMemberStatus
import one.ztd.workbench.kernel.common.context.InstanceContextSummary
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.web.api.context.ApiVersion
import one.ztd.workbench.web.api.context.TenantContextSummary
import one.ztd.workbench.web.api.context.TenantRequestContext

class ManageTenantPeopleControllerTest :
  StringSpec({
    val members = mockk<TenantMemberManagementService>()
    val managedInvitations = mockk<ManagedInvitationService>()
    val controller =
      ManageTenantPeopleController(members, mockk<InvitationService>(), managedInvitations)
    val member =
      TenantMemberView(
        id = "tmb_01JABCDEFGHJKMNPQRSTVWXYZ0",
        user =
          UserSummary(
            PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ1"),
            "Ada",
            "ada@example.test",
          ),
        status = TenantMemberStatus.ACTIVE,
        administrator = true,
        joinedAt = OffsetDateTime.parse("2026-07-15T00:00:00Z"),
      )

    "maps member lifecycle operations" {
      coEvery { members.list(TENANT_ID) } returns listOf(member)
      coEvery { members.suspendMember(TENANT_ID, member.id) } returns
        member.copy(status = TenantMemberStatus.SUSPENDED)
      coEvery { members.restoreMember(TENANT_ID, member.id) } returns member
      coEvery { members.removeMember(TENANT_ID, member.id) } returns
        member.copy(status = TenantMemberStatus.REMOVED)

      runBlocking { controller.listMembers(CONTEXT).single().administrator } shouldBe true
      runBlocking { controller.suspendMember(member.id, CONTEXT).status } shouldBe "SUSPENDED"
      runBlocking { controller.restoreMember(member.id, CONTEXT).status } shouldBe "ACTIVE"
      runBlocking { controller.removeMember(member.id, CONTEXT).status } shouldBe "REMOVED"
    }

    "maps and cancels pending invitations" {
      val invitation =
        ManagedInvitationView(
          id = "inv_01JABCDEFGHJKMNPQRSTVWXYZ0",
          type = InvitationType.TENANT_MEMBER,
          email = "new@example.test",
          displayName = "New member",
          expiresAt = OffsetDateTime.parse("2026-07-22T00:00:00Z"),
          createdAt = OffsetDateTime.parse("2026-07-15T00:00:00Z"),
        )
      coEvery { managedInvitations.listPending(TENANT_ID) } returns listOf(invitation)
      coEvery { managedInvitations.cancel(TENANT_ID, invitation.id) } returns Unit

      runBlocking { controller.listInvitations(CONTEXT).single().email } shouldBe "new@example.test"
      runBlocking { controller.cancelInvitation(invitation.id, CONTEXT) }
      coVerify { managedInvitations.cancel(TENANT_ID, invitation.id) }
    }
  }) {
  private companion object {
    val TENANT_ID: UUID = UUID.randomUUID()
    val CONTEXT =
      TenantRequestContext(
        requestId = "request",
        apiVersion = ApiVersion.Default,
        actor = null,
        receivedAt = Instant.parse("2026-07-15T00:00:00Z"),
        instance = InstanceContextSummary("ins_01JABCDEFGHJKMNPQRSTVWXYZ0", "Workbench"),
        tenant =
          TenantContextSummary(
            TENANT_ID,
            PublicId("ten_01JABCDEFGHJKMNPQRSTVWXYZ0"),
            "Acme",
            "acme",
          ),
      )
  }
}
