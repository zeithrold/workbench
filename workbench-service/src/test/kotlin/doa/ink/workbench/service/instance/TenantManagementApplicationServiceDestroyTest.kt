package doa.ink.workbench.service.instance

import doa.ink.workbench.core.common.ids.PublicId
import doa.ink.workbench.core.identity.TenantRepository
import doa.ink.workbench.core.identity.UserRepository
import doa.ink.workbench.core.identity.model.TenantRecord
import doa.ink.workbench.core.identity.model.TenantStatus
import doa.ink.workbench.core.identity.model.UserRecord
import doa.ink.workbench.core.tenant.events.TenantDestroyRequestedEvent
import doa.ink.workbench.core.tenant.events.TenantDomainEvents
import doa.ink.workbench.security.identity.TenantLoginMethodService
import doa.ink.workbench.security.identity.UserLookupService
import doa.ink.workbench.service.messaging.support.RecordingDomainEventPublisher
import doa.ink.workbench.tenant.tenant.TenantService
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

class TenantManagementApplicationServiceDestroyTest :
  StringSpec({
    val clock = Clock.fixed(Instant.parse("2026-07-03T00:00:00Z"), ZoneOffset.UTC)
    val tenants = mockk<TenantRepository>()
    val users = mockk<UserRepository>()
    val publisher = RecordingDomainEventPublisher()

    val tenantId = UUID.randomUUID()
    val actorId = UUID.randomUUID()
    val tenant =
      TenantRecord(
        id = tenantId,
        apiId = PublicId("ten_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        slug = "acme",
        name = "Acme",
        status = TenantStatus.ACTIVE,
        createdAt = OffsetDateTime.parse("2026-07-03T00:00:00Z"),
        updatedAt = OffsetDateTime.parse("2026-07-03T00:00:00Z"),
      )
    val destroying = tenant.copy(status = TenantStatus.DESTROYING)
    val actor =
      UserRecord(
        id = actorId,
        apiId = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ1"),
        displayName = "Admin",
        primaryEmail = "admin@example.test",
      )

    val service =
      TenantManagementApplicationService(
        tenants = TenantService(tenants),
        tenantLoginMethods = mockk<TenantLoginMethodService>(relaxed = true),
        userLookupService = UserLookupService(users),
        adminUserService = mockk(relaxed = true),
        invitationService = mockk(relaxed = true),
        domainEventPublisher = publisher,
        clock = clock,
      )

    beforeEach {
      publisher.clear()
      coEvery { tenants.findByApiIdForAdmin("ten_01JABCDEFGHJKMNPQRSTVWXYZ0") } returns tenant
      coEvery { users.findById(actorId) } returns actor
      coEvery { tenants.markDestroying(tenantId) } returns destroying
    }

    "requestDestroy marks destroying and publishes event" {
      runBlocking {
        val result =
          service.requestDestroy(
            tenantPublicId = "ten_01JABCDEFGHJKMNPQRSTVWXYZ0",
            actorUserId = actorId,
            deleteReason = "cleanup",
          )

        result.status shouldBe TenantStatus.DESTROYING
        publisher.published.size shouldBe 1
        val published = publisher.published.single()
        published.spec shouldBe TenantDomainEvents.DestroyRequested
        published.payload shouldBe
          TenantDestroyRequestedEvent(
            tenantId = "ten_01JABCDEFGHJKMNPQRSTVWXYZ0",
            requestedBy = "usr_01JABCDEFGHJKMNPQRSTVWXYZ1",
            deleteReason = "cleanup",
            requestedAt = "2026-07-03T00:00Z",
          )
      }
    }
  })
