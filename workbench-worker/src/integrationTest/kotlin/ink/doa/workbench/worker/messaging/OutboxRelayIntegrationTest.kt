package ink.doa.workbench.worker.messaging

import ink.doa.workbench.data.messaging.PostgresOutboxPublicationRepository
import ink.doa.workbench.jobs.messaging.DomainEventExecutionService
import ink.doa.workbench.jobs.messaging.MessagingProperties
import ink.doa.workbench.testsupport.postgres.MigrationSpec
import ink.doa.workbench.testsupport.postgres.WorkbenchPostgresTestSupport
import ink.doa.workbench.testsupport.postgres.jdbcTemplate
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import java.util.concurrent.CompletableFuture
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult

class OutboxRelayIntegrationTest :
  StringSpec({
    "relay rebuilds and publishes locator state from an unfinished PostgreSQL delivery" {
      WorkbenchPostgresTestSupport.openDatabase(MigrationSpec.Full).use { lease ->
        val jdbc = lease.jdbcTemplate()
        val id = UUID.randomUUID()
        jdbc.update(
          """
          INSERT INTO domain_outbox
            (id, event_id, event_type, event_version, topic, partition_key, payload,
             status, created_at, updated_at, next_attempt_at, attempts)
          VALUES (?, ?, 'tenant.created', 1, 'tenant-topic', 'tenant-1', '{}'::jsonb,
                  'PENDING', now(), now(), now(), 0)
          """
            .trimIndent(),
          id,
          "evt_$id",
        )
        jdbc.update(
          """
          INSERT INTO domain_event_deliveries
            (outbox_id, consumer_name, partition_key, status, next_attempt_at, created_at, updated_at)
          VALUES (?, 'tenant-worker', 'tenant-1', 'PENDING', now(), now(), now())
          """
            .trimIndent(),
          id,
        )
        val kafka = mockk<KafkaTemplate<String, String>>()
        every { kafka.send("tenant-topic", "tenant-1", any()) } returns
          CompletableFuture.completedFuture(mockk<SendResult<String, String>>(relaxed = true))

        OutboxRelay(
            PostgresOutboxPublicationRepository(jdbc),
            mockk<DomainEventExecutionService>(relaxed = true),
            MessagingProperties(epoch = "deploy-test"),
            kafka,
          )
          .relay()

        jdbc.queryForObject(
          "SELECT status FROM outbox_transport_publications WHERE outbox_id = ?",
          String::class.java,
          id,
        ) shouldBe "PUBLISHED"
      }
    }
  })
