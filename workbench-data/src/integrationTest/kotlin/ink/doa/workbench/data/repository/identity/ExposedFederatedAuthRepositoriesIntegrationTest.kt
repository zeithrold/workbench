package ink.doa.workbench.data.repository.identity

import ink.doa.workbench.data.support.seedTenant
import ink.doa.workbench.data.support.withPostgresDatabase
import ink.doa.workbench.identity.model.CreateAuthLoginStateCommand
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime

class ExposedFederatedAuthRepositoriesIntegrationTest :
  StringSpec({
    "auth login state repository creates consumes and expires states" {
      withPostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val loginMethods = ExposedLoginMethodRepository(database)
        val loginMethod = loginMethods.findLoginMethodByCode("password").shouldNotBeNull()
        val repository = ExposedAuthLoginStateRepository(database)
        val expiresAt = OffsetDateTime.parse("2026-07-04T12:00:00Z")
        val created =
          repository.create(
            CreateAuthLoginStateCommand(
              stateHash = "state-hash",
              tenantId = tenantId,
              loginMethodId = loginMethod.id,
              redirectUri = "https://app.example.com/callback",
              pkceVerifier = "verifier",
              returnUrl = "https://app.example.com/return",
              expiresAt = expiresAt,
            )
          )

        repository
          .findActiveByStateHash("state-hash", OffsetDateTime.parse("2026-07-04T11:00:00Z"))
          .shouldNotBeNull()
          .id shouldBe created.id

        repository.consume(created.id, OffsetDateTime.parse("2026-07-04T11:30:00Z")) shouldBe true
        repository
          .findActiveByStateHash("state-hash", OffsetDateTime.parse("2026-07-04T11:00:00Z"))
          .shouldBeNull()
      }
    }

    "magic link token repository creates and resolves active token" {
      withPostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val loginMethods = ExposedLoginMethodRepository(database)
        val loginMethod = loginMethods.findLoginMethodByCode("password").shouldNotBeNull()
        val repository = ExposedMagicLinkTokenRepository(database)
        val expiresAt = OffsetDateTime.parse("2026-07-04T12:00:00Z")

        repository.create(
          tokenHash = "token-hash",
          loginMethodId = loginMethod.id,
          tenantId = tenantId,
          normalizedSubject = "ada@example.test",
          expiresAt = expiresAt,
        )

        repository
          .findActiveByHash("token-hash", OffsetDateTime.parse("2026-07-04T11:00:00Z"))
          .shouldNotBeNull()
          .normalizedSubject shouldBe "ada@example.test"
      }
    }
  })
