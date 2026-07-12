package ink.doa.workbench.service.messaging.support

import java.time.Duration
import java.util.Properties
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import kotlin.time.Duration.Companion.milliseconds

object KafkaTestSupport {
  fun publish(
    bootstrapServers: String,
    topic: String,
    key: String,
    value: String,
  ) {
    val properties =
      Properties().apply {
        put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        put(ProducerConfig.ACKS_CONFIG, "all")
      }
    KafkaProducer<String, String>(properties).use { producer ->
      producer.send(ProducerRecord(topic, key, value)).get(30, TimeUnit.SECONDS)
      producer.flush()
    }
  }

  suspend fun awaitCondition(
    timeout: Duration = Duration.ofSeconds(10),
    poll: Duration = Duration.ofMillis(100),
    condition: suspend () -> Boolean,
  ) {
    val deadline = System.nanoTime() + timeout.toNanos()
    while (System.nanoTime() < deadline) {
      if (condition()) return
      delay(poll.toMillis().milliseconds)
    }
    error("Condition not met within $timeout")
  }
}
