package one.ztd.workbench.worker.messaging

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import one.ztd.workbench.application.jobs.messaging.DomainEventDispatcher
import one.ztd.workbench.application.jobs.messaging.DomainEventHandler
import one.ztd.workbench.application.jobs.messaging.TenantEventRouter
import one.ztd.workbench.application.jobs.messaging.TenantJobRegistration
import one.ztd.workbench.application.messaging.support.KafkaTestSupport
import one.ztd.workbench.application.messaging.support.MessagingIntegrationFixtures
import one.ztd.workbench.kernel.messaging.DomainEventDecoder
import one.ztd.workbench.kernel.messaging.DomainEventEncoder
import one.ztd.workbench.kernel.messaging.DomainTopics
import one.ztd.workbench.kernel.messaging.EventMetadata
import one.ztd.workbench.tenant.tenant.events.TenantCreatedEvent
import one.ztd.workbench.tenant.tenant.events.TenantDomainEvents
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.MessageListener

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
      DomainEventHandler<one.ztd.workbench.tenant.tenant.events.TenantDestroyRequestedEvent> {}
    val pipeline =
      DomainEventDispatcher(
        decoder,
        listOf(TenantJobRegistration(TenantEventRouter(decoder, createdHandler, destroyHandler))),
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
        MessageListener<String, String> { record ->
          runBlocking { pipeline.dispatch(record.value()) }
        }
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
