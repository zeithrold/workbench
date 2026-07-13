package ink.doa.workbench.application.messaging

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
          createdAt = now,
          retentionUntil = now.plusDays(30),
        )

      record.id shouldBe id
      record.retentionUntil shouldBe now.plusDays(30)
    }

    "outbox message query defaults limit and offset" {
      val query = OutboxMessageQuery(tenantId = "ten_1", eventType = "work_item.updated")

      query.limit shouldBe 50
      query.offset shouldBe 0L
    }
  })
