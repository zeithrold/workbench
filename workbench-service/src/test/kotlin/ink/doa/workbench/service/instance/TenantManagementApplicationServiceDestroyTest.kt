package ink.doa.workbench.service.instance

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.TenantRepository
import ink.doa.workbench.core.identity.UserRepository
import ink.doa.workbench.core.identity.model.TenantRecord
import ink.doa.workbench.core.identity.model.TenantStatus
import ink.doa.workbench.core.identity.model.UserRecord
import ink.doa.workbench.core.tenant.events.TenantDestroyRequestedEvent
import ink.doa.workbench.security.identity.TenantLoginMethodService
import ink.doa.workbench.security.identity.UserLookupService
import ink.doa.workbench.tenant.tenant.TenantService
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
        dependencies =
          TenantManagementDependencies(
            tenants = TenantService(tenants),
            identity =
              TenantIdentityDependencies(
                tenantLoginMethods = mockk<TenantLoginMethodService>(relaxed = true),
                userLookupService = UserLookupService(users),
                adminUserService = mockk(relaxed = true),
                invitationService = mockk(relaxed = true),
              ),
            defaultWorkItemTemplate = mockk(relaxed = true),
            clock = clock,
          )
      )

    beforeEach {
      coEvery { tenants.findByApiIdForAdmin("ten_01JABCDEFGHJKMNPQRSTVWXYZ0") } returns tenant
      coEvery { users.findById(actorId) } returns actor
      coEvery {
        tenants.requestDestroy(
          tenantId = tenantId,
          tenantApiId = tenant.apiId.value,
          payload =
            TenantDestroyRequestedEvent(
              tenantId = "ten_01JABCDEFGHJKMNPQRSTVWXYZ0",
              requestedBy = "usr_01JABCDEFGHJKMNPQRSTVWXYZ1",
              deleteReason = "cleanup",
              requestedAt = "2026-07-03T00:00Z",
            ),
        )
      } returns destroying
    }

    "requestDestroy marks destroying and enqueues outbox event" {
      runBlocking {
        val result =
          service.requestDestroy(
            tenantPublicId = "ten_01JABCDEFGHJKMNPQRSTVWXYZ0",
            actorUserId = actorId,
            deleteReason = "cleanup",
          )

        result.status shouldBe TenantStatus.DESTROYING
      }
    }
  })
