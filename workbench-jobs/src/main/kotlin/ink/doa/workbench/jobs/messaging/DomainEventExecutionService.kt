package ink.doa.workbench.jobs.messaging

import ink.doa.workbench.core.port.messaging.ClaimedEventDelivery
import ink.doa.workbench.core.port.messaging.DomainEventExecutionStore
import ink.doa.workbench.core.port.messaging.EventDeliveryFailure
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@ink.doa.workbench.jobs.JobsEnabled
@Service
class DomainEventExecutionService(
  private val store: DomainEventExecutionStore,
  private val dispatcher: DomainEventDispatcher,
  private val properties: MessagingProperties,
) {
  private val logger = LoggerFactory.getLogger(javaClass)

  fun materialize() = store.materialize(dispatcher.subscriptions())

  fun drainReady(): Int {
    materialize()
    val now = now()
    val claimed = store.claimReady(properties.batchSize, now, now.plus(properties.leaseDuration))
    claimed.forEach(::execute)
    return claimed.size
  }

  fun drainTransportReady(): Int {
    val now = now()
    val claimed =
      store.claimTransportReady(properties.batchSize, now, now.plus(properties.leaseDuration))
    claimed.forEach(::execute)
    return claimed.size
  }

  fun executeLocator(outboxId: UUID, consumerName: String? = null): Int {
    materialize()
    val now = now()
    store.markTransportNotified(outboxId, consumerName, now)
    val claimed =
      store.claimByOutbox(outboxId, consumerName, now, now.plus(properties.leaseDuration))
    claimed.forEach(::execute)
    return claimed.size
  }

  private fun execute(delivery: ClaimedEventDelivery) {
    try {
      runBlocking { dispatcher.dispatch(delivery.payload, delivery.consumerName) }
      store.markSucceeded(delivery.outboxId, delivery.consumerName, now())
    } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
      val attempts = delivery.attempts + 1
      store.markFailed(
        EventDeliveryFailure(
          delivery.outboxId,
          delivery.consumerName,
          attempts,
          now().plusSeconds(retryDelay(attempts)),
          error.message ?: error.javaClass.simpleName,
          properties.maxAttempts,
        )
      )
      logger.error(
        "domain_event_delivery_failed outboxId={} consumer={} attempts={}",
        delivery.outboxId,
        delivery.consumerName,
        attempts,
        error,
      )
    }
  }

  private fun now(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)

  private fun retryDelay(attempts: Int): Long =
    (1L shl attempts.coerceAtMost(6)).coerceAtMost(MAX_RETRY_DELAY_SECONDS)

  private companion object {
    const val MAX_RETRY_DELAY_SECONDS = 300L
  }
}
