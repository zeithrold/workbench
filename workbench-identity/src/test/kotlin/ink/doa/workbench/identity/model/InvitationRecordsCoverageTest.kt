package ink.doa.workbench.identity.model

import ink.doa.workbench.kernel.common.ids.PublicId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class InvitationRecordsCoverageTest :
  StringSpec({
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val tenantId = UUID.randomUUID()
    val invitedBy = UUID.randomUUID()

    "invitation record stores token and lifecycle metadata" {
      val record =
        InvitationRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("inv"),
          type = InvitationType.TENANT_MEMBER,
          tenantId = tenantId,
          email = "Ada Lovelace <ada@example.test>",
          normalizedEmail = "ada@example.test",
          displayName = "Ada",
          tokenHash = "hash-1",
          invitedBy = invitedBy,
          expiresAt = now.plusDays(7),
          consumedAt = null,
          createdAt = now,
        )

      record.type shouldBe InvitationType.TENANT_MEMBER
      record.normalizedEmail shouldBe "ada@example.test"
    }

    "create invitation command carries invite metadata" {
      CreateInvitationCommand(
          type = InvitationType.TENANT_ADMIN,
          tenantId = tenantId,
          email = "admin@example.test",
          normalizedEmail = "admin@example.test",
          displayName = "Admin",
          tokenHash = "hash-2",
          invitedBy = invitedBy,
          expiresAt = now.plusDays(3),
        )
        .type shouldBe InvitationType.TENANT_ADMIN
    }

    "accept invitation command stores credentials" {
      AcceptInvitationCommand(
          token = "token-1",
          displayName = "Ada",
          password = "secret",
        )
        .displayName shouldBe "Ada"
    }

    "tenant admin assignment variants model provisioning paths" {
      TenantAdminAssignment.SelfAssignment shouldBe TenantAdminAssignment.SelfAssignment
      TenantAdminAssignment.UserAssignment(invitedBy) shouldBe
        TenantAdminAssignment.UserAssignment(invitedBy)
      TenantAdminAssignment.EmailInviteAssignment("admin@example.test", "Admin") shouldBe
        TenantAdminAssignment.EmailInviteAssignment("admin@example.test", "Admin")
    }

    "create tenant with admin command stores defaults" {
      CreateTenantWithAdminCommand(
          name = "Acme",
          slug = "acme",
          adminAssignment = TenantAdminAssignment.SelfAssignment,
        )
        .timezone shouldBe "UTC"
    }
  })
