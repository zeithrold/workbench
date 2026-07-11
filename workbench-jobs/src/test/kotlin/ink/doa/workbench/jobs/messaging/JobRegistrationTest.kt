package ink.doa.workbench.jobs.messaging

import ink.doa.workbench.core.messaging.DomainEventDecoder
import ink.doa.workbench.core.messaging.DomainEventEnvelope
import ink.doa.workbench.core.messaging.DomainTopics
import ink.doa.workbench.core.project.events.ProjectDomainEvents
import ink.doa.workbench.core.sprint.events.SprintCloseRequestedEvent
import ink.doa.workbench.core.sprint.events.SprintDomainEvents
import ink.doa.workbench.core.tenant.events.TenantDomainEvents
import ink.doa.workbench.jobs.sprint.SprintCloseRequestedEventHandler
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

class JobRegistrationTest :
  StringSpec({
    val envelope =
      DomainEventEnvelope(
        eventId = "evt_1",
        type = TenantDomainEvents.Created.type,
        occurredAt = "2026-07-12T00:00:00Z",
        payload = JsonObject(emptyMap()),
      )

    "tenant registration exposes its subscription and delegates" {
      val router = mockk<TenantEventRouter>()
      coEvery { router.route(envelope) } returns Unit
      val registration = TenantJobRegistration(router)

      registration.consumerName shouldBe "tenant-jobs"
      registration.topic shouldBe DomainTopics.TENANT
      registration.eventTypes shouldBe
        setOf(TenantDomainEvents.Created.type, TenantDomainEvents.DestroyRequested.type)
      runBlocking { registration.handle(envelope) }

      coVerify { router.route(envelope) }
    }

    "project registration exposes its subscription and delegates" {
      val router = mockk<ProjectEventRouter>()
      coEvery { router.route(envelope) } returns Unit
      val registration = ProjectJobRegistration(router)

      registration.consumerName shouldBe "project-jobs"
      registration.topic shouldBe DomainTopics.PROJECT
      registration.eventTypes shouldBe setOf(ProjectDomainEvents.DestroyRequested.type)
      runBlocking { registration.handle(envelope) }

      coVerify { router.route(envelope) }
    }

    "sprint registration decodes and delegates" {
      val decoder = mockk<DomainEventDecoder>()
      val handler = mockk<SprintCloseRequestedEventHandler>()
      val event = SprintCloseRequestedEvent("ten_1", "prj_1", "spr_1", "op_1", "usr_1")
      every { decoder.decode(SprintDomainEvents.CloseRequested, envelope) } returns event
      coEvery { handler.handle(event) } returns Unit

      val registration = SprintJobRegistration(decoder, handler)
      runBlocking { registration.handle(envelope) }

      coVerify { handler.handle(event) }
    }
  })
