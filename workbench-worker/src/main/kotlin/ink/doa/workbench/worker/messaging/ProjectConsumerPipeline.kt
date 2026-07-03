package ink.doa.workbench.worker.messaging

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.messaging.DomainEventDecoder
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ProjectConsumerPipeline(
  private val decoder: DomainEventDecoder,
  private val router: ProjectEventRouter,
) {
  private val logger = LoggerFactory.getLogger(javaClass)

  fun run(record: ConsumerRecord<String, String>) {
    runBlocking {
      val started = System.nanoTime()
      try {
        val envelope = decoder.parseEnvelope(record.value())
        router.route(envelope)
        logger.info(
          "domain_event_consumed topic={} partition={} offset={} type={} eventId={} durationMs={}",
          record.topic(),
          record.partition(),
          record.offset(),
          envelope.type,
          envelope.eventId,
          elapsedMillis(started),
        )
      } catch (error: InvalidRequestException) {
        logger.error(
          "domain_event_decode_failed topic={} partition={} offset={} durationMs={}",
          record.topic(),
          record.partition(),
          record.offset(),
          elapsedMillis(started),
          error,
        )
      }
    }
  }

  private fun elapsedMillis(started: Long): Long = (System.nanoTime() - started) / 1_000_000
}
