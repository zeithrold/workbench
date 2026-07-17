package one.ztd.workbench.application.messaging

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.util.UUID

class MessagingLedgerTypesTest :
  StringSpec({
    "execution and publication records retain their ledger identity" {
      val id = UUID.randomUUID()
      val now = OffsetDateTime.parse("2026-07-12T00:00:00Z")
      ClaimedEventDelivery(id, "consumer", "key", "{}", 2).outboxId shouldBe id
      EventSubscription("consumer", "topic", setOf("type")).topic shouldBe "topic"
      EventDeliveryFailure(id, "consumer", 3, now, "error", 8).attempts shouldBe 3
      ClaimedOutboxPublication(id, "topic", "key", 1).partitionKey shouldBe "key"
      OutboxPublicationFailure(id, MessagingBackend.REDIS_STREAMS, "epoch", 2, now, "error", 8)
        .backend shouldBe MessagingBackend.REDIS_STREAMS
      MessagingBackend.entries shouldBe listOf(MessagingBackend.REDIS_STREAMS)
    }
  })
