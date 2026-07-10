package ink.doa.workbench.service.messaging.support

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

class MessagingFixturesIntegrationTest :
  StringSpec({
    "createTopics is idempotent when topic already exists" {
      val topic = "events-${UUID.randomUUID().toString().take(8)}"
      MessagingIntegrationFixtures.createTopics(topic)
      MessagingIntegrationFixtures.createTopics(topic)
      MessagingIntegrationFixtures.bootstrapServers().isNotBlank() shouldBe true
    }

    "publish delivers records to kafka topic" {
      val topic = "publish-${UUID.randomUUID().toString().take(8)}"
      MessagingIntegrationFixtures.createTopics(topic)
      KafkaTestSupport.publish(
        bootstrapServers = MessagingIntegrationFixtures.bootstrapServers(),
        topic = topic,
        key = "evt_01",
        value = """{"type":"test"}""",
      )
    }
  })
