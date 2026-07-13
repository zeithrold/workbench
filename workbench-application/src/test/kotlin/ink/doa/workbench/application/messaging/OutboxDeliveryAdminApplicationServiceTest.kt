package ink.doa.workbench.application.messaging

import ink.doa.workbench.kernel.common.errors.ResourceConflictException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.OffsetDateTime
import java.util.UUID

class OutboxDeliveryAdminApplicationServiceTest :
  StringSpec({
    val store = mockk<OutboxDeliveryAdminStore>()
    val service = OutboxDeliveryAdminApplicationService(store)
    val record = record()

    "list delegates to the delivery store" {
      val query = OutboxDeliveryQuery(status = "DEAD")
      every { store.listDeliveries(query) } returns listOf(record)
      service.list(query) shouldBe listOf(record)
    }

    "replay returns the reset delivery" {
      every { store.replayDeadDelivery(record.outboxId, record.consumerName) } returns true
      every { store.listDeliveries(any()) } returns listOf(record.copy(status = "PENDING"))
      service.replay(record.outboxId, record.consumerName).status shouldBe "PENDING"
    }

    "replay rejects a delivery that is not dead" {
      every { store.replayDeadDelivery(record.outboxId, record.consumerName) } returns false
      shouldThrow<ResourceConflictException> {
        service.replay(record.outboxId, record.consumerName)
      }
    }
  })

private fun record(): OutboxDeliveryRecord {
  val now = OffsetDateTime.parse("2026-07-12T00:00:00Z")
  return OutboxDeliveryRecord(
    UUID.randomUUID(),
    "consumer",
    "key",
    "DEAD",
    8,
    now,
    null,
    "failed",
    null,
    now,
    now,
  )
}
