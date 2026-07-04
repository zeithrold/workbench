package ink.doa.workbench.data.messaging

import ink.doa.workbench.core.messaging.DomainEventEncoder
import ink.doa.workbench.core.messaging.EventMetadata
import ink.doa.workbench.core.tenant.events.TenantCreatedEvent
import ink.doa.workbench.core.tenant.events.TenantDomainEvents
import io.kotest.core.spec.style.StringSpec
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.springframework.kafka.core.KafkaTemplate

class KafkaDomainEventPublisherTest :
  StringSpec({
    val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
    val encoder = DomainEventEncoder(clock)

    "publish skips when kafka template is missing" {
      val publisher = KafkaDomainEventPublisher(encoder, kafkaTemplate = null)

      publisher.publish(
        TenantDomainEvents.Created,
        key = "ten_abc",
        payload =
          TenantCreatedEvent(
            tenantId = "ten_abc",
            name = "Acme",
            status = "active",
            createdAt = "2026-07-04T00:00:00Z",
          ),
        metadata = EventMetadata(traceId = "trace-1", tenantId = "ten_abc"),
      )
    }

    "publish sends encoded payload when kafka template is configured" {
      val kafkaTemplate = mockk<KafkaTemplate<String, String>>(relaxed = true)
      val publisher = KafkaDomainEventPublisher(encoder, kafkaTemplate)

      publisher.publish(
        TenantDomainEvents.Created,
        key = "ten_abc",
        payload =
          TenantCreatedEvent(
            tenantId = "ten_abc",
            name = "Acme",
            status = "active",
            createdAt = "2026-07-04T00:00:00Z",
          ),
        metadata = EventMetadata(traceId = "trace-1", tenantId = "ten_abc"),
      )

      verify { kafkaTemplate.send(TenantDomainEvents.Created.topic, "ten_abc", any()) }
    }
  })
