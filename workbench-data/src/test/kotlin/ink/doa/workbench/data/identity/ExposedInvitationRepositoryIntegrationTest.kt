package ink.doa.workbench.data.identity

import ink.doa.workbench.core.identity.model.CreateInvitationCommand
import ink.doa.workbench.core.identity.model.InvitationType
import ink.doa.workbench.data.support.seedTenant
import ink.doa.workbench.data.support.seedUser
import ink.doa.workbench.data.support.withPostgresDatabase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.junit.jupiter.api.Tag

@Tag("integration")
class ExposedInvitationRepositoryIntegrationTest :
  StringSpec({
    "create find consume and cancel pending invitations" {
      withPostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val inviterId = seedUser(database)
        val repository = ExposedInvitationRepository(database)
        val expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(7)
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        val created =
          repository.create(
            CreateInvitationCommand(
              type = InvitationType.TENANT_MEMBER,
              tenantId = tenantId,
              email = "Invitee@Example.Test",
              normalizedEmail = "invitee@example.test",
              displayName = "Invitee",
              tokenHash = "invite-token-hash",
              invitedBy = inviterId,
              expiresAt = expiresAt,
            )
          )

        created.apiId.value.startsWith("inv_") shouldBe true
        repository.findActiveByHash("invite-token-hash", now).shouldNotBeNull()

        repository.consume(created.id, now) shouldBe true
        repository.findActiveByHash("invite-token-hash", now).shouldBeNull()
        repository.consume(created.id, now) shouldBe false

        val pending =
          repository.create(
            CreateInvitationCommand(
              type = InvitationType.TENANT_ADMIN,
              tenantId = tenantId,
              email = "admin@example.test",
              normalizedEmail = "admin@example.test",
              displayName = null,
              tokenHash = "admin-token-hash",
              invitedBy = inviterId,
              expiresAt = expiresAt,
            )
          )
        pending.id shouldBe pending.id

        repository.cancelPendingByTenant(tenantId, now) shouldBe 1
        repository.findActiveByHash("admin-token-hash", now).shouldBeNull()
      }
    }

    "findActiveByHash ignores expired invitations" {
      withPostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val inviterId = seedUser(database)
        val repository = ExposedInvitationRepository(database)
        val expiredAt = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1)

        repository.create(
          CreateInvitationCommand(
            type = InvitationType.TENANT_MEMBER,
            tenantId = tenantId,
            email = "expired@example.test",
            normalizedEmail = "expired@example.test",
            displayName = null,
            tokenHash = "expired-token-hash",
            invitedBy = inviterId,
            expiresAt = expiredAt,
          )
        )

        repository
          .findActiveByHash("expired-token-hash", OffsetDateTime.now(ZoneOffset.UTC))
          .shouldBeNull()
      }
    }
  })
