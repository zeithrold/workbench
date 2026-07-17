package one.ztd.workbench.identity.invitation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import one.ztd.workbench.kernel.common.context.RequestHost

class InvitationLinkBuilderTest :
  StringSpec({
    "build uses configured public base url" {
      val builder = InvitationLinkBuilder(InvitationLinkProperties("https://app.example.test/"))

      builder.build("/path/{id}", mapOf("id" to "abc"), null) shouldBe
        "https://app.example.test/path/abc"
    }

    "buildInvitationLink substitutes token in path" {
      val builder =
        InvitationLinkBuilder(InvitationLinkProperties(publicBaseUrl = "https://workbench.test"))

      builder.buildInvitationLink("secret-token", null) shouldBe
        "https://workbench.test/invitations/secret-token"
    }

    "build falls back to request host when base url is blank" {
      val builder = InvitationLinkBuilder(InvitationLinkProperties("   "))
      val host = RequestHost(scheme = "https", host = "tenant.workbench.test")

      builder.build("/invite/{token}", mapOf("token" to "tok"), host) shouldBe
        "https://tenant.workbench.test/invite/tok"
    }

    "build requires request host when base url is not configured" {
      val builder = InvitationLinkBuilder(InvitationLinkProperties(publicBaseUrl = null))

      shouldThrow<IllegalArgumentException> {
        builder.buildInvitationLink("token", null)
      }
    }
  })
