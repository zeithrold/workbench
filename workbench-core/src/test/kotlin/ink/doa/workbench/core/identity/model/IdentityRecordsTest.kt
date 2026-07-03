package ink.doa.workbench.core.identity.model

import ink.doa.workbench.core.common.ids.PublicId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

class IdentityRecordsTest :
  StringSpec({
    "authenticated principal can represent session authentication" {
      val user = UserRecord(UUID.randomUUID(), PublicId.new("usr"), "Ada", "ada@example.test")

      val principal =
        AuthenticatedPrincipal(
          user = user,
          loginAccountId = UUID.randomUUID(),
          sessionId = "session-1",
          bearerTokenId = null,
        )

      principal.user.displayName shouldBe "Ada"
      principal.sessionId shouldBe "session-1"
    }

    "login account parameter keys accept password and api token hash parameters" {
      LoginAccountParameterKey.PasswordHash.value shouldBe "password_hash"
      LoginAccountParameterKey.ApiTokenHash.value shouldBe "api_token_hash"
      LoginAccountParameterKey("oidc_refresh_token_ref").value shouldBe "oidc_refresh_token_ref"
    }

    "login account parameter keys reject ambiguous names" {
      shouldThrow<IllegalArgumentException> { LoginAccountParameterKey("password-hash") }
      shouldThrow<IllegalArgumentException> { LoginAccountParameterKey("PasswordHash") }
    }

    "user login account bindings model one linked account per row" {
      val userId = UUID.randomUUID()
      val loginAccountId = UUID.randomUUID()
      val binding =
        UserLoginAccountRecord(
          id = UUID.randomUUID(),
          userId = userId,
          loginAccountId = loginAccountId,
          linkedBy = userId,
          linkedAt = java.time.OffsetDateTime.now(),
          unlinkedAt = null,
        )

      binding.userId shouldBe userId
      binding.loginAccountId shouldBe loginAccountId
      binding.unlinkedAt shouldBe null
    }
  })
