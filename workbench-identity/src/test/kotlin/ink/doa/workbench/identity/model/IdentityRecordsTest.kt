package ink.doa.workbench.identity.model

import ink.doa.workbench.kernel.common.ids.PublicId
import ink.doa.workbench.tenant.model.CreateTenantCommand
import ink.doa.workbench.tenant.model.TenantRecord
import ink.doa.workbench.tenant.model.TenantStatus
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

    "tenant record stores slug and locale metadata" {
      val tenantId = UUID.randomUUID()
      val now = java.time.OffsetDateTime.parse("2026-07-04T00:00:00Z")
      val record =
        TenantRecord(
          id = tenantId,
          apiId = PublicId.new("ten"),
          slug = "acme",
          name = "Acme",
          timezone = "UTC",
          locale = "en-US",
          createdAt = now,
          updatedAt = now,
        )

      record.slug shouldBe "acme"
      record.locale shouldBe "en-US"
    }

    "auth command records store bootstrap and login metadata" {
      CreateTenantCommand(name = "Acme", slug = "acme").status shouldBe TenantStatus.ACTIVE
      BootstrapInstanceAdminCommand(
          displayName = "Ada",
          email = "ada@example.test",
          password = "SecurePass12345",
        )
        .email shouldBe "ada@example.test"
      CreateLoginAccountCommand(
          loginMethodId = UUID.randomUUID(),
          subject = "Ada@Example.Test",
          normalizedSubject = "ada@example.test",
        )
        .normalizedSubject shouldBe "ada@example.test"
    }
  })
