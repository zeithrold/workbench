package one.ztd.workbench.data.messaging

import io.kotest.core.spec.style.StringSpec
import io.mockk.mockk
import io.mockk.verify
import one.ztd.workbench.application.messaging.DomainEventOutbox
import one.ztd.workbench.kernel.messaging.EventMetadata
import one.ztd.workbench.tenant.tenant.events.TenantCreatedEvent
import one.ztd.workbench.tenant.tenant.events.TenantDomainEvents

class OutboxDomainEventPublisherTest :
  StringSpec({
    "publish always appends to the transactional outbox" {
      val outbox = mockk<DomainEventOutbox>(relaxed = true)
      val publisher = OutboxDomainEventPublisher(outbox)
      val payload =
        TenantCreatedEvent(
          tenantId = "ten_abc",
          name = "Acme",
          status = "active",
          createdAt = "2026-07-04T00:00:00Z",
        )
      val metadata = EventMetadata(traceId = "trace-1", tenantId = "ten_abc")

      publisher.publish(TenantDomainEvents.Created, "ten_abc", payload, metadata)

      verify { outbox.append(TenantDomainEvents.Created, "ten_abc", payload, metadata) }
    }
  })
