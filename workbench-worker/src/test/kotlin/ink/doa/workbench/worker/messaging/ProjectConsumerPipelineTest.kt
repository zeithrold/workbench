package ink.doa.workbench.worker.messaging

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.messaging.DomainEventDecoder
import ink.doa.workbench.core.messaging.DomainEventEnvelope
import io.kotest.core.spec.style.StringSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonObject
import org.apache.kafka.clients.consumer.ConsumerRecord

class ProjectConsumerPipelineTest :
  StringSpec({
    val record =
      ConsumerRecord("project.events", 0, 7L, "key", """{"type":"project.destroy_requested"}""")

    "run decodes envelope and routes event" {
      val decoder = mockk<DomainEventDecoder>()
      val router = mockk<ProjectEventRouter>()
      val envelope =
        DomainEventEnvelope(
          eventId = "evt_2",
          type = "project.destroy_requested",
          version = 1,
          occurredAt = "2026-07-04T00:00:00Z",
          traceId = null,
          tenantId = null,
          payload = JsonObject(emptyMap()),
        )
      every { decoder.parseEnvelope(any()) } returns envelope
      coEvery { router.route(envelope) } returns Unit

      ProjectConsumerPipeline(decoder, router).run(record)

      coVerify { router.route(envelope) }
    }

    "run swallows invalid request exceptions from decoder" {
      val decoder = mockk<DomainEventDecoder>()
      val router = mockk<ProjectEventRouter>()
      every { decoder.parseEnvelope(any()) } throws
        InvalidRequestException(WorkbenchErrorCode.REQUEST_VALIDATION_FAILED)

      ProjectConsumerPipeline(decoder, router).run(record)

      coVerify(exactly = 0) { router.route(any()) }
    }
  })
