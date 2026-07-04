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

class ConsumerPipelineTest :
  StringSpec({
    val record = ConsumerRecord("tenant.events", 0, 42L, "key", """{"type":"tenant.created"}""")

    "run decodes envelope and routes event" {
      val decoder = mockk<DomainEventDecoder>()
      val router = mockk<TenantEventRouter>()
      val envelope =
        DomainEventEnvelope(
          eventId = "evt_1",
          type = "tenant.created",
          version = 1,
          occurredAt = "2026-07-04T00:00:00Z",
          traceId = null,
          tenantId = null,
          payload = JsonObject(emptyMap()),
        )
      every { decoder.parseEnvelope(any()) } returns envelope
      coEvery { router.route(envelope) } returns Unit

      ConsumerPipeline(decoder, router).run(record)

      coVerify { router.route(envelope) }
    }

    "run swallows invalid request exceptions from decoder" {
      val decoder = mockk<DomainEventDecoder>()
      val router = mockk<TenantEventRouter>()
      every { decoder.parseEnvelope(any()) } throws
        InvalidRequestException(WorkbenchErrorCode.REQUEST_VALIDATION_FAILED)

      ConsumerPipeline(decoder, router).run(record)

      coVerify(exactly = 0) { router.route(any()) }
    }
  })
