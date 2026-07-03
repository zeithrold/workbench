package ink.doa.workbench.core.messaging

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.tenant.events.TenantCreatedEvent
import ink.doa.workbench.core.tenant.events.TenantDestroyRequestedEvent
import ink.doa.workbench.core.tenant.events.TenantDomainEvents
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DomainEventCodecTest :
  StringSpec({
    val clock = Clock.fixed(Instant.parse("2026-07-03T00:00:00Z"), ZoneOffset.UTC)
    val encoder = DomainEventEncoder(clock)
    val decoder = DomainEventDecoder()

    "round-trips tenant.created payload through envelope" {
      val payload =
        TenantCreatedEvent(
          tenantId = "ten_abc",
          name = "Acme",
          status = "active",
          createdAt = "2026-07-03T00:00:00Z",
        )
      val json =
        encoder.encode(
          TenantDomainEvents.Created,
          payload,
          EventMetadata(traceId = "trace-1", tenantId = "ten_abc"),
        )
      val envelope = decoder.parseEnvelope(json)
      envelope.type shouldBe "tenant.created"
      envelope.version shouldBe 1
      envelope.traceId shouldBe "trace-1"
      envelope.tenantId shouldBe "ten_abc"
      decoder.decode(TenantDomainEvents.Created, envelope) shouldBe payload
    }

    "decodes golden tenant.created fixture" {
      val json =
        this::class.java.classLoader.getResource("events/tenant.created.v1.json")!!.readText()
      val envelope = decoder.parseEnvelope(json)
      decoder.decode(TenantDomainEvents.Created, envelope) shouldBe
        TenantCreatedEvent(
          tenantId = "ten_sample123",
          name = "Acme",
          status = "active",
          createdAt = "2026-07-03T00:00:00Z",
        )
    }

    "round-trips tenant.destroy_requested payload through envelope" {
      val payload =
        TenantDestroyRequestedEvent(
          tenantId = "ten_abc",
          requestedBy = "usr_actor",
          deleteReason = "test",
          requestedAt = "2026-07-03T00:00:00Z",
        )
      val json =
        encoder.encode(
          TenantDomainEvents.DestroyRequested,
          payload,
          EventMetadata(traceId = "trace-2", tenantId = "ten_abc"),
        )
      val envelope = decoder.parseEnvelope(json)
      envelope.type shouldBe "tenant.destroy_requested"
      decoder.decode(TenantDomainEvents.DestroyRequested, envelope) shouldBe payload
    }

    "decodes golden tenant.destroy_requested fixture" {
      val json =
        this::class
          .java
          .classLoader
          .getResource("events/tenant.destroy_requested.v1.json")!!
          .readText()
      val envelope = decoder.parseEnvelope(json)
      decoder.decode(TenantDomainEvents.DestroyRequested, envelope) shouldBe
        TenantDestroyRequestedEvent(
          tenantId = "ten_sample123",
          requestedBy = "usr_01JABCDEFGHJKMNPQRSTVWXYZ1",
          deleteReason = "Customer churned",
          requestedAt = "2026-07-03T00:00:00Z",
        )
    }

    "rejects mismatched event type" {
      val payload =
        TenantCreatedEvent(
          tenantId = "ten_abc",
          name = "Acme",
          status = "active",
          createdAt = "2026-07-03T00:00:00Z",
        )
      val json = encoder.encode(TenantDomainEvents.Created, payload)
      val envelope = decoder.parseEnvelope(json).copy(type = "tenant.updated")
      shouldThrow<InvalidRequestException> {
        decoder.decode(TenantDomainEvents.Created, envelope)
      }
    }

    "rejects unsupported event version" {
      val payload =
        TenantCreatedEvent(
          tenantId = "ten_abc",
          name = "Acme",
          status = "active",
          createdAt = "2026-07-03T00:00:00Z",
        )
      val json = encoder.encode(TenantDomainEvents.Created, payload)
      val envelope = decoder.parseEnvelope(json).copy(version = 99)
      shouldThrow<InvalidRequestException> {
        decoder.decode(TenantDomainEvents.Created, envelope)
      }
    }

    "rejects invalid envelope json" {
      shouldThrow<InvalidRequestException> { decoder.parseEnvelope("{not-json") }
    }
  })
