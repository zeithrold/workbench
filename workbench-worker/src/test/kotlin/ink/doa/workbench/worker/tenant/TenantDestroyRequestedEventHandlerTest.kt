package ink.doa.workbench.worker.tenant

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.TenantRepository
import ink.doa.workbench.core.identity.UserRepository
import ink.doa.workbench.core.identity.model.TenantRecord
import ink.doa.workbench.core.identity.model.TenantStatus
import ink.doa.workbench.core.identity.model.UserRecord
import ink.doa.workbench.core.port.locking.DistributedLockService
import ink.doa.workbench.core.tenant.events.TenantDestroyRequestedEvent
import ink.doa.workbench.core.tenant.events.TenantDomainEvents
import ink.doa.workbench.service.messaging.support.RecordingDomainEventPublisher
import ink.doa.workbench.tenant.instance.TenantDestructionService
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking

class TenantDestroyRequestedEventHandlerTest :
  StringSpec({
    val tenants = mockk<TenantRepository>()
    val users = mockk<UserRepository>()
    val tenantDestructionService = mockk<TenantDestructionService>()
    val publisher = RecordingDomainEventPublisher()
    val distributedLockService =
      object : DistributedLockService {
        override fun <T> withLock(
          name: String,
          wait: Duration,
          lease: Duration,
          block: () -> T,
        ): T = block()
      }
    val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
    val handler =
      TenantDestroyRequestedEventHandler(
        tenants,
        users,
        tenantDestructionService,
        publisher,
        distributedLockService,
        clock,
      )

    beforeTest {
      publisher.clear()
    }

    "handle executes destruction and publishes destroyed event" {
      val tenant = sampleTenant()
      val actor = sampleUser()
      val payload =
        TenantDestroyRequestedEvent(
          tenantId = tenant.apiId.value,
          requestedBy = actor.apiId.value,
          deleteReason = "cleanup",
          requestedAt = "2026-07-04T00:00:00Z",
        )

      coEvery { tenants.findByApiIdForAdmin(tenant.apiId.value) } returns tenant
      coEvery { users.findByApiId(actor.apiId.value) } returns actor
      coEvery {
        tenantDestructionService.execute(
          tenantId = tenant.id,
          deletedBy = actor.id,
          deleteReason = "cleanup",
        )
      } returns true

      runBlocking { handler.handle(payload) }

      publisher.published.single().spec shouldBe TenantDomainEvents.Destroyed
    }

    "handle skips when tenant is missing" {
      coEvery { tenants.findByApiIdForAdmin("ten_missing") } returns null

      runBlocking {
        handler.handle(
          TenantDestroyRequestedEvent(
            tenantId = "ten_missing",
            requestedBy = "usr_missing",
            deleteReason = null,
            requestedAt = "2026-07-04T00:00:00Z",
          )
        )
      }

      publisher.published shouldBe emptyList()
    }

    "handle skips when actor is missing" {
      val tenant = sampleTenant()
      coEvery { tenants.findByApiIdForAdmin(tenant.apiId.value) } returns tenant
      coEvery { users.findByApiId("usr_missing") } returns null

      runBlocking {
        handler.handle(
          TenantDestroyRequestedEvent(
            tenantId = tenant.apiId.value,
            requestedBy = "usr_missing",
            deleteReason = null,
            requestedAt = "2026-07-04T00:00:00Z",
          )
        )
      }

      publisher.published shouldBe emptyList()
    }
  })

private fun sampleTenant(): TenantRecord =
  TenantRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("ten"),
    slug = "acme",
    name = "Acme",
    status = TenantStatus.DESTROYING,
    createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
    updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
  )

private fun sampleUser(): UserRecord =
  UserRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("usr"),
    displayName = "Ada",
    primaryEmail = "ada@example.test",
  )
