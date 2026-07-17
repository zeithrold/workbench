package one.ztd.workbench.application.messaging

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException

class OutboxAdminApplicationServiceTest :
  StringSpec({
    val store = mockk<OutboxAdminStore>()
    val service = OutboxAdminApplicationService(store)
    val id = UUID.randomUUID()
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    "get throws when message is missing" {
      every { store.findById(id) } returns null

      shouldThrow<ResourceNotFoundException> { service.get(id) }
    }

    "get returns immutable outbox message" {
      val record =
        OutboxMessageRecord(
          id,
          "evt_1",
          "work_item.updated",
          "workbench.work-item",
          "wki_1",
          "ten_1",
          now,
          now.plusDays(30),
        )
      every { store.findById(id) } returns record

      service.get(id) shouldBe record
    }
  })
