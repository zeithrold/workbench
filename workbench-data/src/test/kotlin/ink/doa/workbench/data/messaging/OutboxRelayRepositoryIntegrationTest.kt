package ink.doa.workbench.data.messaging

import ink.doa.workbench.testsupport.postgres.MigrationSpec
import ink.doa.workbench.testsupport.postgres.WorkbenchPostgresTestSupport
import ink.doa.workbench.testsupport.postgres.jdbcTemplate
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Tags("integration")
class OutboxRelayRepositoryIntegrationTest :
  StringSpec({
    fun insertOutbox(
      jdbc: org.springframework.jdbc.core.JdbcTemplate,
      id: UUID,
      status: String,
      attempts: Int = 0,
      nextAttemptAt: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1),
      lockedUntil: OffsetDateTime? = null,
    ) {
      jdbc.update(
        """
        INSERT INTO domain_outbox (
          id, event_id, event_type, event_version, topic, partition_key, payload,
          status, created_at, updated_at, next_attempt_at, attempts, locked_until
        )
        VALUES (?, 'evt_$id', 'work_item.updated', 1, 'workbench.work-item', 'wki_1',
                '{}'::jsonb, ?, now(), now(), ?, ?, ?)
        """
          .trimIndent(),
        id,
        status,
        nextAttemptAt,
        attempts,
        lockedUntil,
      )
    }

    "claim returns pending messages and sets lock" {
      WorkbenchPostgresTestSupport.openDatabase(MigrationSpec.Full).use { lease ->
        val jdbc = lease.jdbcTemplate()
        val relay = OutboxRelayRepository(jdbc)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val id = UUID.randomUUID()
        insertOutbox(jdbc, id, "PENDING")

        val claimed = relay.claim(limit = 5, now = now, lockedUntil = now.plusMinutes(5))
        claimed shouldHaveSize 1
        claimed.single().id shouldBe id
        claimed.single().topic shouldBe "workbench.work-item"

        relay.claim(limit = 5, now = now, lockedUntil = now.plusMinutes(5)) shouldHaveSize 0
      }
    }

    "markPublished marks message published" {
      WorkbenchPostgresTestSupport.openDatabase(MigrationSpec.Full).use { lease ->
        val jdbc = lease.jdbcTemplate()
        val relay = OutboxRelayRepository(jdbc)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val id = UUID.randomUUID()
        insertOutbox(jdbc, id, "PENDING")

        relay.claim(limit = 1, now = now, lockedUntil = now.plusMinutes(5))
        relay.markPublished(id, now)

        jdbc.queryForObject(
          "SELECT status FROM domain_outbox WHERE id = ?",
          String::class.java,
          id,
        ) shouldBe "PUBLISHED"
      }
    }

    "markFailed schedules retry or dead based on attempts" {
      WorkbenchPostgresTestSupport.openDatabase(MigrationSpec.Full).use { lease ->
        val jdbc = lease.jdbcTemplate()
        val relay = OutboxRelayRepository(jdbc)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val retryId = UUID.randomUUID()
        val deadId = UUID.randomUUID()
        insertOutbox(jdbc, retryId, "PENDING")
        insertOutbox(jdbc, deadId, "PENDING")

        relay.markFailed(
          retryId,
          attempts = 3,
          nextAttemptAt = now.plusMinutes(10),
          error = "broker",
        )
        relay.markFailed(
          deadId,
          attempts = OutboxRelayRepository.MAX_ATTEMPTS,
          nextAttemptAt = now.plusMinutes(10),
          error = "exhausted",
        )

        jdbc.queryForObject(
          "SELECT status FROM domain_outbox WHERE id = ?",
          String::class.java,
          retryId,
        ) shouldBe "RETRY"
        jdbc.queryForObject(
          "SELECT status FROM domain_outbox WHERE id = ?",
          String::class.java,
          deadId,
        ) shouldBe "DEAD"
      }
    }
  })
