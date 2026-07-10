package ink.doa.workbench.worker.messaging

import ink.doa.workbench.core.messaging.DomainEventEncoder
import ink.doa.workbench.core.tenant.events.TenantCreatedEvent
import ink.doa.workbench.core.tenant.events.TenantDomainEvents
import ink.doa.workbench.data.messaging.OutboxRelayRepository
import ink.doa.workbench.service.messaging.support.MessagingIntegrationFixtures
import ink.doa.workbench.testsupport.postgres.MigrationSpec
import ink.doa.workbench.testsupport.postgres.WorkbenchPostgresTestSupport
import ink.doa.workbench.testsupport.postgres.jdbcTemplate
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CompletableFuture
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult

class OutboxRelayIntegrationTest :
  StringSpec({
    val clock = Clock.fixed(Instant.parse("2026-07-10T12:00:00Z"), ZoneOffset.UTC)
    val encoder = DomainEventEncoder(clock)
    val topic = "outbox-relay-${UUID.randomUUID().toString().take(8)}"

    beforeSpec { MessagingIntegrationFixtures.createTopics(topic) }

    fun insertPendingOutbox(jdbc: org.springframework.jdbc.core.JdbcTemplate): UUID {
      val id = UUID.randomUUID()
      val payload =
        encoder.encode(
          TenantDomainEvents.Created,
          TenantCreatedEvent(
            tenantId = "ten_relay",
            name = "Relay Tenant",
            status = "active",
            createdAt = "2026-07-10T12:00:00Z",
          ),
        )
      jdbc.update(
        """
        INSERT INTO domain_outbox (
          id, event_id, event_type, event_version, topic, partition_key, tenant_id,
          payload, status, created_at, updated_at, next_attempt_at, attempts
        )
        VALUES (?, ?, ?, 1, ?, ?, ?, ?::jsonb, 'PENDING', now(), now(), now(), 0)
        """
          .trimIndent(),
        id,
        "evt_${id.toString().replace("-", "")}",
        TenantDomainEvents.Created.type,
        topic,
        "ten_relay",
        "ten_relay",
        payload,
      )
      return id
    }

    "relay publishes pending outbox rows to kafka" {
      WorkbenchPostgresTestSupport.openDatabase(MigrationSpec.Full).use { lease ->
        val jdbc = lease.jdbcTemplate()
        val repository = OutboxRelayRepository(jdbc)
        val kafkaTemplate = mockk<KafkaTemplate<String, String>>()
        val relay = OutboxRelay(repository, kafkaTemplate)
        insertPendingOutbox(jdbc)
        every { kafkaTemplate.send(topic, any(), any()) } returns
          CompletableFuture.completedFuture(mockk<SendResult<String, String>>(relaxed = true))

        relay.relay()

        jdbc.queryForObject(
          "SELECT status FROM domain_outbox ORDER BY created_at DESC LIMIT 1",
          String::class.java,
        ) shouldBe "PUBLISHED"
      }
    }

    fun resetBackoff(jdbc: org.springframework.jdbc.core.JdbcTemplate) {
      jdbc.update(
        "UPDATE domain_outbox SET next_attempt_at = now() WHERE status IN ('PENDING', 'RETRY')"
      )
    }

    "relay retries when kafka is unavailable then publishes after recovery" {
      WorkbenchPostgresTestSupport.openDatabase(MigrationSpec.Full).use { lease ->
        val jdbc = lease.jdbcTemplate()
        val repository = OutboxRelayRepository(jdbc)
        val kafkaTemplate = mockk<KafkaTemplate<String, String>>()
        val relay = OutboxRelay(repository, kafkaTemplate)
        insertPendingOutbox(jdbc)
        var sendAttempts = 0
        every { kafkaTemplate.send(topic, any(), any()) } answers
          {
            sendAttempts += 1
            if (sendAttempts < 3) {
              CompletableFuture.failedFuture(RuntimeException("broker down"))
            } else {
              CompletableFuture.completedFuture(mockk<SendResult<String, String>>(relaxed = true))
            }
          }

        repeat(3) {
          relay.relay()
          resetBackoff(jdbc)
        }

        jdbc.queryForObject(
          "SELECT status FROM domain_outbox ORDER BY created_at DESC LIMIT 1",
          String::class.java,
        ) shouldBe "PUBLISHED"
        jdbc.queryForObject(
          "SELECT attempts FROM domain_outbox ORDER BY created_at DESC LIMIT 1",
          Int::class.java,
        ) shouldBe 2
      }
    }

    "dead letter after max attempts" {
      WorkbenchPostgresTestSupport.openDatabase(MigrationSpec.Full).use { lease ->
        val jdbc = lease.jdbcTemplate()
        val repository = OutboxRelayRepository(jdbc)
        val kafkaTemplate = mockk<KafkaTemplate<String, String>>()
        val relay = OutboxRelay(repository, kafkaTemplate)
        insertPendingOutbox(jdbc)
        every { kafkaTemplate.send(topic, any(), any()) } returns
          CompletableFuture.failedFuture(RuntimeException("broker down"))

        repeat(OutboxRelayRepository.MAX_ATTEMPTS) {
          relay.relay()
          resetBackoff(jdbc)
        }

        jdbc.queryForObject(
          "SELECT status FROM domain_outbox ORDER BY created_at DESC LIMIT 1",
          String::class.java,
        ) shouldBe "DEAD"
      }
    }
  })
