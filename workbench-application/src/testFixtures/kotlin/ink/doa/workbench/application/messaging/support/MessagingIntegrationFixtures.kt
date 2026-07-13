package ink.doa.workbench.application.messaging.support

import java.util.Properties
import java.util.concurrent.TimeUnit
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.errors.TopicExistsException
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName

object MessagingIntegrationFixtures {
  private val kafkaImage = DockerImageName.parse("apache/kafka-native:3.8.0")

  val kafka: KafkaContainer by lazy { KafkaContainer(kafkaImage).also { it.start() } }

  fun bootstrapServers(): String = kafka.bootstrapServers

  fun createTopics(vararg names: String) {
    val properties =
      Properties().apply {
        put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers())
      }
    AdminClient.create(properties).use { admin ->
      try {
        admin
          .createTopics(names.map { NewTopic(it, 1, 1.toShort()) })
          .all()
          .get(30, TimeUnit.SECONDS)
      } catch (error: Exception) {
        if (error.cause !is TopicExistsException && error !is TopicExistsException) {
          throw error
        }
      }
    }
  }
}
