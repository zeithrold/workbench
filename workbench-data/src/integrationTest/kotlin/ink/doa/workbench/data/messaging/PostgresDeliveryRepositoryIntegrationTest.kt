package ink.doa.workbench.data.messaging

import ink.doa.workbench.core.messaging.OutboxDeliveryQuery
import ink.doa.workbench.core.port.messaging.EventDeliveryFailure
import ink.doa.workbench.core.port.messaging.EventSubscription
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
  })

private fun insertMessage(jdbc: org.springframework.jdbc.core.JdbcTemplate): OutboxMessage {
  val id = UUID.randomUUID()
  jdbc.update(
    """
    INSERT INTO domain_outbox (
      id, event_id, event_type, event_version, topic, partition_key, payload,
      status, created_at, updated_at, next_attempt_at, attempts
    ) VALUES (?, ?, 'work_item.updated', 1, 'workbench.work-item', 'wki_1', '{}'::jsonb,
              'PENDING', now(), now(), now(), 0)
    """
      .trimIndent(),
    id,
    "evt_$id",
  )
  return OutboxMessage(id, "workbench.work-item", "work_item.updated", "wki_1", "{}", 0)
}
