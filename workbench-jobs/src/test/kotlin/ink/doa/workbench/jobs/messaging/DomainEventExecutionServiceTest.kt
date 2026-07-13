package ink.doa.workbench.jobs.messaging

import ink.doa.workbench.core.port.messaging.ClaimedEventDelivery
import ink.doa.workbench.core.port.messaging.DomainEventExecutionStore
import ink.doa.workbench.core.port.messaging.EventSubscription
import io.kotest.core.spec.style.StringSpec
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID

class DomainEventExecutionServiceTest :
  StringSpec({
    "successful locator claim executes and completes the authoritative delivery" {
      val store = mockk<DomainEventExecutionStore>(relaxed = true)
      val dispatcher = mockk<DomainEventDispatcher>()
      val id = UUID.randomUUID()
      val delivery = ClaimedEventDelivery(id, "consumer", "key", "{}", 0)
      every { dispatcher.subscriptions() } returns
        listOf(EventSubscription("consumer", "topic", setOf("event")))
      every { store.markTransportNotified(id, "consumer", any()) } returns Unit
      every { store.claimByOutbox(id, "consumer", any(), any()) } returns listOf(delivery)
      coEvery { dispatcher.dispatch("{}", "consumer") } returns Unit

      DomainEventExecutionService(store, dispatcher, MessagingProperties())
        .executeLocator(id, "consumer")

      verify { store.materialize(any()) }
      verify { store.markTransportNotified(id, "consumer", any()) }
      verify { store.markSucceeded(id, "consumer", any()) }
    }

    "handler failure is persisted as delivery retry" {
      val store = mockk<DomainEventExecutionStore>(relaxed = true)
      val dispatcher = mockk<DomainEventDispatcher>()
      val delivery = ClaimedEventDelivery(UUID.randomUUID(), "consumer", "key", "{}", 1)
      every { dispatcher.subscriptions() } returns emptyList()
      every { store.claimReady(any(), any(), any()) } returns listOf(delivery)
      coEvery { dispatcher.dispatch("{}", "consumer") } throws IllegalStateException("failed")

      DomainEventExecutionService(store, dispatcher, MessagingProperties(maxAttempts = 8))
        .drainReady()

      verify {
        store.markFailed(match { it.outboxId == delivery.outboxId && it.attempts == 2 })
      }
    }

    "transport retry drain does not materialize or claim unsignaled deliveries" {
      val store = mockk<DomainEventExecutionStore>(relaxed = true)
      val dispatcher = mockk<DomainEventDispatcher>(relaxed = true)
      every { store.claimTransportReady(any(), any(), any()) } returns emptyList()

      DomainEventExecutionService(store, dispatcher, MessagingProperties()).drainTransportReady()

      verify(exactly = 0) { store.materialize(any()) }
      verify { store.claimTransportReady(any(), any(), any()) }
    }
  })
