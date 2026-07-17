package one.ztd.workbench.web.invitation

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import one.ztd.workbench.application.invitation.InvitationAcceptView
import one.ztd.workbench.application.invitation.InvitationPreviewView
import one.ztd.workbench.identity.common.summary.UserSummary
import one.ztd.workbench.identity.model.InvitationType
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.tenant.common.summary.TenantSummary

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
