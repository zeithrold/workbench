package ink.doa.workbench.core.messaging

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class OutboxMessageRecordTest :
  StringSpec({
    "outbox message record stores query fields" {
      val now = OffsetDateTime.now(ZoneOffset.UTC)
      val id = UUID.randomUUID()
      val record =
        OutboxMessageRecord(
          id = id,
          eventId = "evt_1",
          eventType = "work_item.updated",
          topic = "workbench.work-item",
          partitionKey = "wki_1",
          tenantId = "ten_1",
          status = "DEAD",
          attempts = 3,
          lastError = "timeout",
          createdAt = now,
          updatedAt = now,
          nextAttemptAt = now,
          publishedAt = null,
        )

      record.id shouldBe id
      record.status shouldBe "DEAD"
    }

    "outbox message query defaults limit and offset" {
      val query =
        OutboxMessageQuery(status = "DEAD", tenantId = "ten_1", eventType = "work_item.updated")

      query.limit shouldBe 50
      query.offset shouldBe 0L
      query.status shouldBe "DEAD"
    }
  })
