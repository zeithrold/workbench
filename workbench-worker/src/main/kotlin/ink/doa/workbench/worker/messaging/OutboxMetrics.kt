package ink.doa.workbench.worker.messaging

import ink.doa.workbench.core.port.messaging.OutboxAdminStore
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("worker")
@ConditionalOnProperty(name = ["workbench.outbox.relay-enabled"], havingValue = "true")
class OutboxMetrics(outboxAdmin: OutboxAdminStore, registry: MeterRegistry) {
  init {
    Gauge.builder("workbench.outbox.dead.count", outboxAdmin) { store ->
        store.countByStatus("DEAD").toDouble()
      }
      .description("Number of domain outbox messages in DEAD status")
      .register(registry)
  }
}
