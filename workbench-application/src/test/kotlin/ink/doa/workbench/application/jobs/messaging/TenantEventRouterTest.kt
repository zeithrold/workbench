package ink.doa.workbench.application.jobs.messaging

import ink.doa.workbench.kernel.messaging.DomainEventDecoder
import ink.doa.workbench.kernel.messaging.DomainEventEncoder
import ink.doa.workbench.kernel.messaging.DomainEventEnvelope
import ink.doa.workbench.tenant.tenant.events.TenantCreatedEvent
import ink.doa.workbench.tenant.tenant.events.TenantDestroyRequestedEvent
import ink.doa.workbench.tenant.tenant.events.TenantDomainEvents
import io.kotest.core.spec.style.StringSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class TenantEventRouterTest :
  StringSpec({
    val decoder = DomainEventDecoder()
    val createdHandler = mockk<DomainEventHandler<TenantCreatedEvent>>()
    val destroyHandler = mockk<DomainEventHandler<TenantDestroyRequestedEvent>>()
    val router = TenantEventRouter(decoder, createdHandler, destroyHandler)

    beforeTest {
      clearMocks(createdHandler, destroyHandler)
      coEvery { createdHandler.handle(any()) } returns Unit
      coEvery { destroyHandler.handle(any()) } returns Unit
    }

    "route dispatches tenant created events to handler" {
      val payload =
        TenantCreatedEvent(
          tenantId = "ten_abc",
          name = "Acme",
          status = "active",
          createdAt = "2026-07-04T00:00:00Z",
        )
      val envelope =
        DomainEventEnvelope(
          eventId = "evt_1",
          type = TenantDomainEvents.Created.type,
          version = TenantDomainEvents.Created.currentVersion,
          occurredAt = "2026-07-04T00:00:00Z",
          payload =
            DomainEventEncoder.defaultJson.encodeToJsonElement(
              TenantCreatedEvent.serializer(),
              payload,
            ),
        )

      runBlocking { router.route(envelope) }

      coVerify { createdHandler.handle(payload) }
    }

    "route dispatches destroy requested events to handler" {
      val payload =
        TenantDestroyRequestedEvent(
          tenantId = "ten_abc",
          requestedBy = "usr_abc",
          deleteReason = "cleanup",
          requestedAt = "2026-07-04T00:00:00Z",
        )
      val envelope =
        DomainEventEnvelope(
          eventId = "evt_2",
          type = TenantDomainEvents.DestroyRequested.type,
          version = TenantDomainEvents.DestroyRequested.currentVersion,
          occurredAt = "2026-07-04T00:00:00Z",
          payload =
            DomainEventEncoder.defaultJson.encodeToJsonElement(
              TenantDestroyRequestedEvent.serializer(),
              payload,
            ),
        )

      runBlocking { router.route(envelope) }

      coVerify { destroyHandler.handle(payload) }
    }

    "route ignores unknown event types" {
      val envelope =
        DomainEventEnvelope(
          eventId = "evt_3",
          type = "tenant.unknown",
          occurredAt = "2026-07-04T00:00:00Z",
          payload = JsonObject(mapOf("key" to JsonPrimitive("value"))),
        )

      runBlocking { router.route(envelope) }

      coVerify(exactly = 0) { createdHandler.handle(any()) }
      coVerify(exactly = 0) { destroyHandler.handle(any()) }
    }
  })
