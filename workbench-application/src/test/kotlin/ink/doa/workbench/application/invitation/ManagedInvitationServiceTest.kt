package ink.doa.workbench.application.invitation

import ink.doa.workbench.identity.InvitationRepository
import ink.doa.workbench.identity.model.InvitationRecord
import ink.doa.workbench.identity.model.InvitationType
import ink.doa.workbench.kernel.common.errors.ResourceNotFoundException
import ink.doa.workbench.kernel.common.ids.PublicId
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

class ManagedInvitationServiceTest :
  StringSpec({
    val invitations = mockk<InvitationRepository>()
    val service =
      ManagedInvitationService(
        invitations,
        Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC),
      )
    val record = invitation()

    "lists and cancels pending invitations" {
      coEvery { invitations.listPendingByTenant(record.tenantId, any()) } returns listOf(record)
      coEvery { invitations.cancelPending(record.tenantId, record.apiId.value, any()) } returns true

      runBlocking { service.listPending(record.tenantId).single().email } shouldBe record.email
      runBlocking { service.cancel(record.tenantId, record.apiId.value) }
    }

    "rejects cancellation of a non-pending invitation" {
      coEvery { invitations.cancelPending(record.tenantId, record.apiId.value, any()) } returns
        false
      shouldThrow<ResourceNotFoundException> {
        runBlocking { service.cancel(record.tenantId, record.apiId.value) }
      }
    }
  })

private fun invitation(): InvitationRecord {
  val now = OffsetDateTime.parse("2026-07-15T00:00:00Z")
  return InvitationRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("inv"),
    type = InvitationType.TENANT_MEMBER,
    tenantId = UUID.randomUUID(),
    email = "ada@example.test",
    normalizedEmail = "ada@example.test",
    displayName = "Ada",
    tokenHash = "hash",
    invitedBy = UUID.randomUUID(),
    expiresAt = now.plusDays(7),
    consumedAt = null,
    createdAt = now,
  )
}
