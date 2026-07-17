package one.ztd.workbench.application.messaging

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

class OutboxLocatorTest :
  StringSpec({
    "locator round trips without carrying event content" {
      val id = UUID.randomUUID()
      val encoded = OutboxLocator.encode(id, "deploy-42")

      OutboxLocator.decode(encoded) shouldBe
        OutboxLocator(outboxId = id.toString(), epoch = "deploy-42")
      encoded.contains("payload") shouldBe false
    }

    "locator rejects unsupported versions, invalid ids, and blank epochs" {
      shouldThrow<IllegalArgumentException> {
        OutboxLocator(version = 2, outboxId = UUID.randomUUID().toString(), epoch = "deploy")
      }
      shouldThrow<IllegalArgumentException> { OutboxLocator(outboxId = "bad-id", epoch = "deploy") }
      shouldThrow<IllegalArgumentException> {
        OutboxLocator(outboxId = UUID.randomUUID().toString(), epoch = "")
      }
    }
  })
