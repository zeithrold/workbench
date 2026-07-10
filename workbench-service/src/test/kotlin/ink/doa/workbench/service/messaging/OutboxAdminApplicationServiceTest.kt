package ink.doa.workbench.service.messaging

import ink.doa.workbench.core.common.errors.ResourceConflictException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.messaging.OutboxMessageRecord
import ink.doa.workbench.core.port.messaging.OutboxAdminStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class OutboxAdminApplicationServiceTest :
  StringSpec({
    val store = mockk<OutboxAdminStore>()
    val service = OutboxAdminApplicationService(store)
    val id = UUID.randomUUID()
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val dead =
      OutboxMessageRecord(
        id = id,
        eventId = "evt_1",
        eventType = "work_item.updated",
        topic = "workbench.work-item",
        partitionKey = "wki_1",
        tenantId = "ten_1",
        status = "DEAD",
        attempts = 8,
        lastError = "broker down",
        createdAt = now,
        updatedAt = now,
        nextAttemptAt = now,
        publishedAt = null,
      )

    "replay rejects non-dead messages" {
      every { store.findById(id) } returns dead.copy(status = "RETRY")

      shouldThrow<ResourceConflictException> { service.replay(id) }
    }

    "replay resets dead message" {
      every { store.findById(id) } returns dead andThen dead.copy(status = "RETRY", attempts = 0)
      every { store.replayDead(id) } returns true

      service.replay(id).status shouldBe "RETRY"
      verify { store.replayDead(id) }
    }

    "get throws when message is missing" {
      every { store.findById(id) } returns null

      shouldThrow<ResourceNotFoundException> { service.get(id) }
    }
  })
