package ink.doa.workbench.data.messaging

import ink.doa.workbench.application.messaging.EventDeliveryFailure
import ink.doa.workbench.application.messaging.EventSubscription
import ink.doa.workbench.application.messaging.OutboxDeliveryQuery
import ink.doa.workbench.testsupport.postgres.MigrationSpec
import ink.doa.workbench.testsupport.postgres.WorkbenchPostgresTestSupport
import ink.doa.workbench.testsupport.postgres.jdbcTemplate
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class PostgresDeliveryRepositoryIntegrationTest :
  StringSpec({
    "materialize creates one delivery per matching consumer and supports replay" {
      WorkbenchPostgresTestSupport.openDatabase(MigrationSpec.Full).use { lease ->
        val jdbc = lease.jdbcTemplate()
        val repository = PostgresDeliveryRepository(jdbc)
        val message = insertMessage(jdbc)
        repository.materialize(
          listOf(
            EventSubscription("notifications", message.topic, setOf(message.eventType)),
            EventSubscription("webhooks", message.topic, setOf(message.eventType)),
            EventSubscription("ignored", "another-topic", setOf(message.eventType)),
          )
        )

        val claimed =
          repository.claimReady(
            10,
            OffsetDateTime.now(ZoneOffset.UTC),
            OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(2),
          )
        claimed shouldHaveSize 2

        val first = claimed.first()
        repository.markFailed(
          EventDeliveryFailure(
            first.outboxId,
            first.consumerName,
            attempts = 8,
            nextAttemptAt = OffsetDateTime.now(ZoneOffset.UTC),
            error = "exhausted",
            maxAttempts = 8,
          )
        )
        repository.replayDeadDelivery(first.outboxId, first.consumerName) shouldBe true
        repository
          .listDeliveries(OutboxDeliveryQuery(consumerName = first.consumerName))
          .single()
          .status shouldBe "PENDING"
      }
    }

    "transport drain only claims deliveries signaled by Kafka or Redis" {
      WorkbenchPostgresTestSupport.openDatabase(MigrationSpec.Full).use { lease ->
        val jdbc = lease.jdbcTemplate()
        val repository = PostgresDeliveryRepository(jdbc)
        val notified = insertMessage(jdbc)
        val unsignaled = insertMessage(jdbc)
        val subscription =
          EventSubscription("notifications", notified.topic, setOf(notified.eventType))
        repository.materialize(listOf(subscription))
        repository.markTransportNotified(
          notified.id,
          null,
          OffsetDateTime.now(ZoneOffset.UTC),
        )

        val claimed =
          repository.claimTransportReady(
            10,
            OffsetDateTime.now(ZoneOffset.UTC),
            OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(2),
          )

        claimed.single().outboxId shouldBe notified.id
        claimed.none { it.outboxId == unsignaled.id } shouldBe true
      }
    }
  })

private data class TestOutboxMessage(
  val id: UUID,
  val topic: String,
  val eventType: String,
  val partitionKey: String,
)

private fun insertMessage(jdbc: org.springframework.jdbc.core.JdbcTemplate): TestOutboxMessage {
  val id = UUID.randomUUID()
  jdbc.update(
    """
    INSERT INTO domain_outbox (
      id, event_id, event_type, event_version, topic, partition_key, payload,
      created_at, retention_until
    ) VALUES (?, ?, 'work_item.updated', 1, 'workbench.work-item', 'wki_1', '{}'::jsonb,
              now(), now() + interval '30 days')
    """
      .trimIndent(),
    id,
    "evt_$id",
  )
  return TestOutboxMessage(id, "workbench.work-item", "work_item.updated", "wki_1")
}
