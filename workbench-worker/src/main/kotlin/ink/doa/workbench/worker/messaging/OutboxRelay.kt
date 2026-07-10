package ink.doa.workbench.worker.messaging

import ink.doa.workbench.data.messaging.OutboxRelayRepository
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
@ConditionalOnProperty(name = ["workbench.outbox.relay-enabled"], havingValue = "true")
class OutboxRelay(
  private val repository: OutboxRelayRepository,
  private val kafka: KafkaTemplate<String, String>,
) {
  private val logger = LoggerFactory.getLogger(javaClass)

  @Suppress("TooGenericExceptionCaught")
  @Scheduled(fixedDelayString = "\${workbench.outbox.relay-delay-ms:1000}")
  fun relay() {
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val messages = repository.claim(limit = 50, now = now, lockedUntil = now.plusMinutes(2))
    messages.forEach { message ->
      try {
        kafka.send(message.topic, message.partitionKey, message.payload).get(30, TimeUnit.SECONDS)
        repository.markPublished(message.id, OffsetDateTime.now(ZoneOffset.UTC))
        logger.info(
          "outbox_published id={} topic={} attempts={}",
          message.id,
          message.topic,
          message.attempts,
        )
      } catch (error: Exception) {
        val attempts = message.attempts + 1
        val delaySeconds = (1L shl attempts.coerceAtMost(6)).coerceAtMost(300)
        repository.markFailed(
          id = message.id,
          attempts = attempts,
          nextAttemptAt = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(delaySeconds),
          error = error.message ?: error.javaClass.simpleName,
        )
        if (attempts >= OutboxRelayRepository.MAX_ATTEMPTS) {
          logger.error(
            "outbox_dead_letter id={} topic={} eventType={} attempts={}",
            message.id,
            message.topic,
            message.partitionKey,
            attempts,
            error,
          )
        } else {
          logger.error(
            "outbox_publish_failed id={} topic={} attempts={}",
            message.id,
            message.topic,
            attempts,
            error,
          )
        }
      }
    }
  }
}
