package ink.doa.workbench.worker.messaging

import ink.doa.workbench.core.messaging.DomainEventDecoder
import ink.doa.workbench.core.messaging.DomainEventEncoder
import ink.doa.workbench.core.messaging.DomainTopics
import ink.doa.workbench.core.messaging.EventMetadata
import ink.doa.workbench.core.tenant.events.TenantCreatedEvent
import ink.doa.workbench.core.tenant.events.TenantDomainEvents
import ink.doa.workbench.service.messaging.support.KafkaTestSupport
import ink.doa.workbench.service.messaging.support.MessagingIntegrationFixtures
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.Tag
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.MessageListener

@Tag("integration")
class TenantEventListenerIntegrationTest :
  StringSpec({
    val bootstrapServers = MessagingIntegrationFixtures.bootstrapServers()
    val groupId = "workbench-worker-test-${UUID.randomUUID()}"
    val decoder = DomainEventDecoder()
    val encoder =
      DomainEventEncoder(Clock.fixed(Instant.parse("2026-07-03T00:00:00Z"), ZoneOffset.UTC))
    val processedCount = AtomicInteger(0)
    val createdHandler = DomainEventHandler<TenantCreatedEvent> { processedCount.incrementAndGet() }
    val destroyHandler =
      DomainEventHandler<ink.doa.workbench.core.tenant.events.TenantDestroyRequestedEvent> {}
    val pipeline =
      ConsumerPipeline(
        decoder = decoder,
        router = TenantEventRouter(decoder, createdHandler, destroyHandler),
      )

    lateinit var container: ConcurrentMessageListenerContainer<String, String>

    beforeSpec {
      MessagingIntegrationFixtures.createTopics(DomainTopics.TENANT)
      val consumerProps =
        mapOf<String, Any>(
          ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
          ConsumerConfig.GROUP_ID_CONFIG to groupId,
          ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
          ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
          ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        )
      val factory = DefaultKafkaConsumerFactory<String, String>(consumerProps)
      val containerProps = ContainerProperties(DomainTopics.TENANT)
      containerProps.setMessageListener(
        MessageListener<String, String> { record -> pipeline.run(record) }
      )
      container = ConcurrentMessageListenerContainer(factory, containerProps)
      container.start()
    }

    afterSpec {
      container.stop()
    }

    "consumes tenant.created events from kafka" {
      processedCount.set(0)
      val payload =
        TenantCreatedEvent(
          tenantId = "ten_integration",
          name = "Integration Tenant",
          status = "active",
          createdAt = "2026-07-03T00:00:00Z",
        )
      val json =
        encoder.encode(
          TenantDomainEvents.Created,
          payload,
          EventMetadata(tenantId = payload.tenantId),
        )

      KafkaTestSupport.publish(
        bootstrapServers = bootstrapServers,
        topic = DomainTopics.TENANT,
        key = payload.tenantId,
        value = json,
      )

      runBlocking {
        KafkaTestSupport.awaitCondition {
          processedCount.get() == 1
        }
      }
      processedCount.get() shouldBe 1
    }
  })
