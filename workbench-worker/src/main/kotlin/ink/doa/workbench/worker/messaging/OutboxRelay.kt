package ink.doa.workbench.worker.messaging

import ink.doa.workbench.core.messaging.OutboxLocator
import ink.doa.workbench.core.port.messaging.ClaimedOutboxPublication
import ink.doa.workbench.core.port.messaging.MessagingBackend
import ink.doa.workbench.core.port.messaging.OutboxPublicationFailure
import ink.doa.workbench.core.port.messaging.OutboxPublicationStore
import ink.doa.workbench.jobs.messaging.DomainEventExecutionService
import ink.doa.workbench.jobs.messaging.MessagingProperties
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("worker")
@ConditionalOnProperty(name = ["workbench.messaging.transport"], havingValue = "kafka")
class OutboxRelay(
  private val repository: OutboxPublicationStore,
  private val execution: DomainEventExecutionService,
  private val properties: MessagingProperties,
  private val kafka: KafkaTemplate<String, String>,
) {
  private val logger = LoggerFactory.getLogger(javaClass)

  @Scheduled(fixedDelayString = "\${workbench.messaging.fallback-poll-interval:5s}")
  fun relay() {
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    execution.materialize()
    repository.reconcile(MessagingBackend.KAFKA, properties.epoch, now)
    val messages =
      repository.claim(
        MessagingBackend.KAFKA,
        properties.epoch,
        properties.batchSize,
        now,
        now.plus(properties.leaseDuration),
      )
    messages.forEach(::publish)
  }

  private fun publish(message: ClaimedOutboxPublication) {
    try {
      kafka
        .send(
          message.topic,
          message.partitionKey,
          OutboxLocator.encode(message.outboxId, properties.epoch),
        )
        .get(30, TimeUnit.SECONDS)
      repository.markPublished(
        message.outboxId,
        MessagingBackend.KAFKA,
        properties.epoch,
        OffsetDateTime.now(ZoneOffset.UTC),
      )
      logger.info(
        "outbox_published id={} topic={} attempts={}",
        message.outboxId,
        message.topic,
        message.attempts,
      )
    } catch (
      // Relay boundary: every publisher failure must be persisted for retry or dead-lettering.
      @Suppress("TooGenericExceptionCaught") error: Exception) {
      val attempts = message.attempts + 1
      val delaySeconds = (1L shl attempts.coerceAtMost(6)).coerceAtMost(300)
      repository.markFailed(
        OutboxPublicationFailure(
          message.outboxId,
          MessagingBackend.KAFKA,
          properties.epoch,
          attempts,
          OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(delaySeconds),
          error.message ?: error.javaClass.simpleName,
          properties.maxAttempts,
        )
      )
      if (attempts >= properties.maxAttempts) {
        logger.error(
          "outbox_dead_letter id={} topic={} eventType={} attempts={}",
          message.outboxId,
          message.topic,
          message.partitionKey,
          attempts,
          error,
        )
      } else {
        logger.error(
          "outbox_publish_failed id={} topic={} attempts={}",
          message.outboxId,
          message.topic,
          attempts,
          error,
        )
      }
    }
  }
}
