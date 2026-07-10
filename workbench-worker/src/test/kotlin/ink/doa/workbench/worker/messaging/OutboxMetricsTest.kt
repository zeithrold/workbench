package ink.doa.workbench.worker.messaging

import ink.doa.workbench.core.port.messaging.OutboxAdminStore
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk

class OutboxMetricsTest :
  StringSpec({
    "registers dead outbox gauge from admin store" {
      val store = mockk<OutboxAdminStore>()
      val registry = SimpleMeterRegistry()
      every { store.countByStatus("DEAD") } returns 4

      OutboxMetrics(store, registry)

      registry.get("workbench.outbox.dead.count").gauge().value() shouldBe 4.0
    }
  })
