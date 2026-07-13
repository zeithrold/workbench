package ink.doa.workbench.application.jobs.tenant

import ink.doa.workbench.identity.UserRepository
import ink.doa.workbench.identity.model.UserRecord
import ink.doa.workbench.kernel.common.ids.PublicId
import ink.doa.workbench.kernel.port.locking.DistributedLockService
import ink.doa.workbench.kernel.port.messaging.DomainEventPublisher
import ink.doa.workbench.tenant.TenantRepository
import ink.doa.workbench.tenant.instance.TenantDestructionService
import ink.doa.workbench.tenant.model.TenantRecord
import ink.doa.workbench.tenant.tenant.events.TenantDestroyRequestedEvent
import io.kotest.core.spec.style.StringSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking

class TenantDestroyRequestedEventHandlerTest :
  StringSpec({
    val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
    val now = OffsetDateTime.now(ZoneOffset.UTC)

    "handle skips when tenant is missing" {
      val tenants = mockk<TenantRepository>()
      val users = mockk<UserRepository>()
      coEvery { tenants.findByApiIdForAdmin("ten_missing") } returns null
      val handler =
        TenantDestroyRequestedEventHandler(
          tenants = tenants,
          users = users,
          tenantDestructionService = mockk(relaxed = true),
          domainEventPublisher = mockk(relaxed = true),
          distributedLockService = immediateLockService(),
          clock = clock,
        )

      runBlocking {
        handler.handle(
          TenantDestroyRequestedEvent(
            tenantId = "ten_missing",
            requestedBy = "usr_abc",
            deleteReason = "cleanup",
            requestedAt = "2026-07-04T00:00:00Z",
          )
        )
      }

      coVerify(exactly = 0) { users.findByApiId(any()) }
    }

    "handle skips when actor is missing" {
      val tenants = mockk<TenantRepository>()
      val users = mockk<UserRepository>()
      val destruction = mockk<TenantDestructionService>(relaxed = true)
      val tenant =
        TenantRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("ten"),
          slug = "acme",
          name = "Acme",
          timezone = "UTC",
          locale = "en-US",
          createdAt = now,
          updatedAt = now,
        )
      coEvery { tenants.findByApiIdForAdmin(tenant.apiId.value) } returns tenant
      coEvery { users.findByApiId("usr_missing") } returns null
      val handler =
        TenantDestroyRequestedEventHandler(
          tenants = tenants,
          users = users,
          tenantDestructionService = destruction,
          domainEventPublisher = mockk(relaxed = true),
          distributedLockService = immediateLockService(),
          clock = clock,
        )

      runBlocking {
        handler.handle(
          TenantDestroyRequestedEvent(
            tenantId = tenant.apiId.value,
            requestedBy = "usr_missing",
            deleteReason = "cleanup",
            requestedAt = "2026-07-04T00:00:00Z",
          )
        )
      }

      coVerify { users.findByApiId("usr_missing") }
      coVerify(exactly = 0) { destruction.execute(any(), any(), any()) }
    }

    "handle executes destruction and publishes destroyed event" {
      val tenantId = UUID.randomUUID()
      val userId = UUID.randomUUID()
      val tenants = mockk<TenantRepository>()
      val users = mockk<UserRepository>()
      val destruction = mockk<TenantDestructionService>()
      val publisher = mockk<DomainEventPublisher>(relaxed = true)
      val tenant =
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
      val user =
        UserRecord(
          id = userId,
          apiId = PublicId.new("usr"),
          displayName = "Ada",
          primaryEmail = "ada@example.test",
        )
      coEvery { tenants.findByApiIdForAdmin(tenant.apiId.value) } returns tenant
      coEvery { users.findByApiId(user.apiId.value) } returns user
      coEvery { destruction.execute(tenantId, userId, "cleanup") } returns true
      val handler =
        TenantDestroyRequestedEventHandler(
          tenants = tenants,
          users = users,
          tenantDestructionService = destruction,
          domainEventPublisher = publisher,
          distributedLockService = immediateLockService(),
          clock = clock,
        )

      runBlocking {
        handler.handle(
          TenantDestroyRequestedEvent(
            tenantId = tenant.apiId.value,
            requestedBy = user.apiId.value,
            deleteReason = "cleanup",
            requestedAt = "2026-07-04T00:00:00Z",
          )
        )
      }

      coVerify { destruction.execute(tenantId, userId, "cleanup") }
      verify { publisher.publish(any(), tenant.apiId.value, any(), any()) }
    }

    "handle skips publishing when destruction does not complete" {
      val tenantId = UUID.randomUUID()
      val userId = UUID.randomUUID()
      val tenants = mockk<TenantRepository>()
      val users = mockk<UserRepository>()
      val destruction = mockk<TenantDestructionService>()
      val publisher = mockk<DomainEventPublisher>(relaxed = true)
      val tenant =
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
      val user =
        UserRecord(
          id = userId,
          apiId = PublicId.new("usr"),
          displayName = "Ada",
          primaryEmail = "ada@example.test",
        )
      coEvery { tenants.findByApiIdForAdmin(tenant.apiId.value) } returns tenant
      coEvery { users.findByApiId(user.apiId.value) } returns user
      coEvery { destruction.execute(tenantId, userId, "cleanup") } returns false
      val handler =
        TenantDestroyRequestedEventHandler(
          tenants = tenants,
          users = users,
          tenantDestructionService = destruction,
          domainEventPublisher = publisher,
          distributedLockService = immediateLockService(),
          clock = clock,
        )

      runBlocking {
        handler.handle(
          TenantDestroyRequestedEvent(
            tenantId = tenant.apiId.value,
            requestedBy = user.apiId.value,
            deleteReason = "cleanup",
            requestedAt = "2026-07-04T00:00:00Z",
          )
        )
      }

      coVerify { destruction.execute(tenantId, userId, "cleanup") }
      verify(exactly = 0) { publisher.publish(any(), any(), any(), any()) }
    }
  })

private fun immediateLockService(): DistributedLockService =
  object : DistributedLockService {
    override fun <T> withLock(
      name: String,
      wait: java.time.Duration,
      lease: java.time.Duration,
      block: () -> T,
    ): T = block()
  }
