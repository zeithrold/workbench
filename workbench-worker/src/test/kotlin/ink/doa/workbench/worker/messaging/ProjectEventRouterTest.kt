package ink.doa.workbench.worker.messaging

import ink.doa.workbench.core.messaging.DomainEventDecoder
import ink.doa.workbench.core.messaging.DomainEventEncoder
import ink.doa.workbench.core.messaging.DomainEventEnvelope
import ink.doa.workbench.core.project.events.ProjectDestroyRequestedEvent
import ink.doa.workbench.core.project.events.ProjectDomainEvents
import io.kotest.core.spec.style.StringSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

class ProjectEventRouterTest :
  StringSpec({
    val decoder = DomainEventDecoder()
    val destroyHandler = mockk<DomainEventHandler<ProjectDestroyRequestedEvent>>()
    val router = ProjectEventRouter(decoder, destroyHandler)

    beforeTest {
      clearMocks(destroyHandler)
      coEvery { destroyHandler.handle(any()) } returns Unit
    }

    "route dispatches destroy requested events to handler" {
      val payload =
        ProjectDestroyRequestedEvent(
          tenantId = "ten_abc",
          projectId = "prj_abc",
          requestedBy = "usr_abc",
          deleteReason = "cleanup",
          requestedAt = "2026-07-04T00:00:00Z",
        )
      val envelope =
        DomainEventEnvelope(
          eventId = "evt_1",
          type = ProjectDomainEvents.DestroyRequested.type,
          version = ProjectDomainEvents.DestroyRequested.currentVersion,
          occurredAt = "2026-07-04T00:00:00Z",
          payload =
            DomainEventEncoder.defaultJson.encodeToJsonElement(
              ProjectDestroyRequestedEvent.serializer(),
              payload,
            ),
        )

      runBlocking { router.route(envelope) }

      coVerify { destroyHandler.handle(payload) }
    }

    "route ignores unknown event types" {
      val envelope =
        DomainEventEnvelope(
          eventId = "evt_2",
          type = "project.unknown",
          occurredAt = "2026-07-04T00:00:00Z",
          payload = JsonObject(mapOf("key" to JsonPrimitive("value"))),
        )

      runBlocking { router.route(envelope) }

      coVerify(exactly = 0) { destroyHandler.handle(any()) }
    }
  })
