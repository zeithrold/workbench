package ink.doa.workbench.worker.messaging

import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.consumer.ConsumerRecord

class EventListenerTest :
  StringSpec({
    val record = ConsumerRecord("events", 0, 1L, "key", """{"type":"tenant.created"}""")

    "tenant listener delegates to consumer pipeline" {
      val pipeline = mockk<ConsumerPipeline>(relaxed = true)
      every { pipeline.run(record) } returns Unit

      TenantEventListener(pipeline).onMessage(record)

      verify { pipeline.run(record) }
    }

    "project listener delegates to project consumer pipeline" {
      val pipeline = mockk<ProjectConsumerPipeline>(relaxed = true)
      every { pipeline.run(record) } returns Unit

      ProjectEventListener(pipeline).onMessage(record)

      verify { pipeline.run(record) }
    }
  })
