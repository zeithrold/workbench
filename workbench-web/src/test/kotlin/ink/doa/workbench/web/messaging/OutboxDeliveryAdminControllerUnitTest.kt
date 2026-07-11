package ink.doa.workbench.web.messaging

import ink.doa.workbench.core.common.context.InstanceRequestContext
import ink.doa.workbench.core.messaging.OutboxDeliveryQuery
import ink.doa.workbench.core.messaging.OutboxDeliveryRecord
import ink.doa.workbench.service.messaging.OutboxDeliveryAdminApplicationService
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking

class OutboxDeliveryAdminControllerUnitTest :
  StringSpec({
    val service = mockk<OutboxDeliveryAdminApplicationService>()
    val controller = OutboxDeliveryAdminController(service)
    val context = mockk<InstanceRequestContext>()
    val record = deliveryRecord()

    "list maps delivery records" {
      val query = OutboxDeliveryQuery(status = "DEAD")
      every { service.list(query) } returns listOf(record)

      val response = runBlocking { controller.list(query, context) }.single()

      response.outboxId shouldBe record.outboxId
      response.consumerName shouldBe record.consumerName
      response.status shouldBe record.status
      response.lastError shouldBe record.lastError
    }

    "replay maps the updated delivery" {
      every { service.replay(record.outboxId, record.consumerName) } returns
        record.copy(status = "PENDING", attempts = 0)

      val response = runBlocking {
        controller.replay(record.outboxId, record.consumerName, context)
      }

      response.status shouldBe "PENDING"
      response.attempts shouldBe 0
    }
  })

private fun deliveryRecord(): OutboxDeliveryRecord {
  val now = OffsetDateTime.parse("2026-07-12T00:00:00Z")
  return OutboxDeliveryRecord(
    outboxId = UUID.randomUUID(),
    consumerName = "notifications",
    partitionKey = "wki_1",
    status = "DEAD",
    attempts = 8,
    nextAttemptAt = now,
    lockedUntil = null,
    lastError = "failed",
    completedAt = null,
    createdAt = now,
    updatedAt = now,
  )
}
