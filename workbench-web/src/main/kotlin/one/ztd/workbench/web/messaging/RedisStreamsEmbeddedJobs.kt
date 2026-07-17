package one.ztd.workbench.web.messaging

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import one.ztd.workbench.application.jobs.messaging.DomainEventDispatcher
import one.ztd.workbench.application.jobs.messaging.DomainEventExecutionService
import one.ztd.workbench.application.jobs.messaging.MessagingProperties
import one.ztd.workbench.application.messaging.EventSubscription
import one.ztd.workbench.application.messaging.MessagingBackend
import one.ztd.workbench.application.messaging.OutboxLocator
import one.ztd.workbench.application.messaging.OutboxPublicationFailure
import one.ztd.workbench.application.messaging.OutboxPublicationStore
import org.redisson.api.RStream
import org.redisson.api.RedissonClient
import org.redisson.api.stream.StreamAddArgs
import org.redisson.api.stream.StreamCreateGroupArgs
import org.redisson.api.stream.StreamMessageId
import org.redisson.api.stream.StreamReadGroupArgs
import org.redisson.client.RedisException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["workbench.messaging.transport"], havingValue = "redis-streams")
class RedisStreamsEmbeddedJobs(
  private val redisson: RedissonClient,
  private val publications: OutboxPublicationStore,
  private val execution: DomainEventExecutionService,
  private val dispatcher: DomainEventDispatcher,
  private val properties: MessagingProperties,
) {
  private val logger = LoggerFactory.getLogger(javaClass)
  private val consumerName = "web-${System.getenv("HOSTNAME") ?: UUID.randomUUID()}"

  @Scheduled(fixedDelayString = "\${workbench.messaging.fallback-poll-interval:5s}")
  fun runOnce() {
    relayOutbox()
    execution.materialize()
    execution.drainTransportReady()
    // Groups are materialized from the shared registration list by the execution service.
    subscriptions().forEach(::consume)
  }

  private fun relayOutbox() {
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    execution.materialize()
    publications.reconcile(MessagingBackend.REDIS_STREAMS, properties.epoch, now)
    publications
      .claim(
        MessagingBackend.REDIS_STREAMS,
        properties.epoch,
        properties.batchSize,
        now,
        now.plus(properties.leaseDuration),
      )
      .forEach { message ->
        try {
          stream(message.topic)
            .add(
              StreamAddArgs.entries(
                mapOf("locator" to OutboxLocator.encode(message.outboxId, properties.epoch))
              )
            )
          publications.markPublished(
            message.outboxId,
            MessagingBackend.REDIS_STREAMS,
            properties.epoch,
            OffsetDateTime.now(ZoneOffset.UTC),
          )
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
          val attempts = message.attempts + 1
          publications.markFailed(
            OutboxPublicationFailure(
              message.outboxId,
              MessagingBackend.REDIS_STREAMS,
              properties.epoch,
              attempts,
              OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(retryDelay(attempts)),
              error.message ?: error.javaClass.simpleName,
              properties.maxAttempts,
            )
          )
        }
      }
  }

  private fun subscriptions(): List<EventSubscription> = dispatcher.subscriptions()

  private fun consume(subscription: EventSubscription) {
    val stream = stream(subscription.topic)
    ensureGroup(stream, subscription.consumerName)
    val messages =
      stream.readGroup(
        subscription.consumerName,
        consumerName,
        StreamReadGroupArgs.neverDelivered().count(properties.batchSize),
      )
    messages.forEach { (id, fields) -> process(stream, subscription, id, fields) }
    val reclaimed =
      stream.autoClaim(
        subscription.consumerName,
        consumerName,
        properties.leaseDuration.toMillis(),
        java.util.concurrent.TimeUnit.MILLISECONDS,
        StreamMessageId.MIN,
        properties.batchSize,
      )
    reclaimed.messages.forEach { (id, fields) -> process(stream, subscription, id, fields) }
  }

  private fun process(
    stream: RStream<String, String>,
    subscription: EventSubscription,
    id: StreamMessageId,
    fields: Map<String, String>,
  ) {
    val locator = fields["locator"]?.let(OutboxLocator::decode) ?: return
    try {
      execution.executeLocator(UUID.fromString(locator.outboxId), subscription.consumerName)
      stream.ack(subscription.consumerName, id)
    } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
      logger.error(
        "redis_stream_locator_failed stream={} consumer={} id={}",
        stream.name,
        subscription.consumerName,
        id,
        error,
      )
    }
  }

  private fun ensureGroup(stream: RStream<String, String>, group: String) {
    try {
      stream.createGroup(StreamCreateGroupArgs.name(group).makeStream())
    } catch (error: RedisException) {
      if (!error.message.orEmpty().contains("BUSYGROUP")) throw error
    }
  }

  private fun stream(topic: String): RStream<String, String> =
    redisson.getStream("workbench:events:${topic.replace('.', ':')}")

  private fun retryDelay(attempts: Int): Long =
    (1L shl attempts.coerceAtMost(6)).coerceAtMost(MAX_RETRY_DELAY_SECONDS)

  companion object {
    private const val MAX_RETRY_DELAY_SECONDS = 300L
  }
}
