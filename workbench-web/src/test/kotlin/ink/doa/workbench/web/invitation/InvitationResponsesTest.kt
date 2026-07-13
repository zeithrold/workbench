package ink.doa.workbench.web.invitation

import ink.doa.workbench.application.invitation.InvitationAcceptView
import ink.doa.workbench.application.invitation.InvitationPreviewView
import ink.doa.workbench.identity.common.summary.UserSummary
import ink.doa.workbench.identity.model.InvitationType
import ink.doa.workbench.kernel.common.ids.PublicId
import ink.doa.workbench.tenant.common.summary.TenantSummary
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class InvitationResponsesTest :
  StringSpec({
    val tenant = TenantSummary(id = PublicId.new("ten"), name = "Acme", slug = "acme")
    val user =
      UserSummary(id = PublicId.new("usr"), displayName = "Ada", primaryEmail = "ada@example.test")

    "invitation preview response maps view" {
      InvitationPreviewResponse.from(
          InvitationPreviewView(
            type = InvitationType.TENANT_ADMIN,
            tenant = tenant,
            email = "ada@example.test",
            displayName = "Ada",
          )
        )
        .email shouldBe "ada@example.test"
    }

    "invitation accept response maps view" {
      InvitationAcceptResponse.from(
          InvitationAcceptView(type = InvitationType.TENANT_MEMBER, tenant = tenant, user = user)
        )
        .user
        .displayName shouldBe "Ada"
    }
  })
