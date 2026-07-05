package ink.doa.workbench.worker.messaging

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.messaging.DomainEventDecoder
import ink.doa.workbench.core.messaging.DomainEventEncoder
import ink.doa.workbench.core.messaging.DomainTopics
import ink.doa.workbench.core.messaging.EventMetadata
import ink.doa.workbench.core.workitem.WorkItemActivityRepository
import ink.doa.workbench.core.workitem.activity.CreateWorkItemActivityCommand
import ink.doa.workbench.core.workitem.activity.ListWorkItemActivitiesQuery
import ink.doa.workbench.core.workitem.activity.PendingWorkItemActivity
import ink.doa.workbench.core.workitem.activity.WorkItemActivityCodec
import ink.doa.workbench.core.workitem.activity.WorkItemActivityDomainEvents
import ink.doa.workbench.core.workitem.activity.WorkItemActivityEntityRef
import ink.doa.workbench.core.workitem.activity.WorkItemActivityListPage
import ink.doa.workbench.core.workitem.activity.WorkItemActivityRecord
import ink.doa.workbench.core.workitem.activity.WorkItemActivityRecordRequestedEvent
import ink.doa.workbench.core.workitem.activity.WorkItemActivitySpecs
import ink.doa.workbench.core.workitem.activity.WorkItemActivityStatusRef
import ink.doa.workbench.core.workitem.activity.WorkItemActivityStatusSnapshot
import ink.doa.workbench.core.workitem.activity.WorkItemCreatedPayload
import ink.doa.workbench.service.messaging.support.KafkaTestSupport
import ink.doa.workbench.service.messaging.support.MessagingIntegrationFixtures
import ink.doa.workbench.worker.workitem.WorkItemActivityRecordRequestedHandler
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
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
class WorkItemActivityEventListenerIntegrationTest :
  StringSpec({
    val bootstrapServers = MessagingIntegrationFixtures.bootstrapServers()
    val groupId = "workbench-worker-activity-test-${UUID.randomUUID()}"
    val decoder = DomainEventDecoder()
    val encoder =
      DomainEventEncoder(Clock.fixed(Instant.parse("2026-07-03T00:00:00Z"), ZoneOffset.UTC))
    val codec = WorkItemActivityCodec()
    val processedCount = AtomicInteger(0)
    val activities =
      object : WorkItemActivityRepository {
        override suspend fun <T : Any> create(
          command: CreateWorkItemActivityCommand<T>
        ): WorkItemActivityRecord {
          error("not used")
        }

        override suspend fun createWithId(
          pending: PendingWorkItemActivity
        ): WorkItemActivityRecord {
          processedCount.incrementAndGet()
          return WorkItemActivityRecord(
            id = pending.id,
            apiId = pending.apiId,
            tenantId = pending.command.tenantId,
            projectId = pending.command.projectId,
            workItemId = pending.command.workItemId,
            workItemApiId = PublicId.new("iss"),
            actorUserId = pending.command.actorUserId,
            actorApiId = pending.command.actorUserId?.let { PublicId.new("usr") },
            actorDisplayName = "Actor",
            activityType = pending.command.spec.type,
            occurredAt = pending.command.occurredAt,
            summary = pending.command.summary,
            payload =
              ink.doa.workbench.core.workitem.activity.WorkItemActivityPayload.Created(
                WorkItemCreatedPayload(
                  status =
                    WorkItemActivityStatusSnapshot(
                      from = null,
                      to =
                        WorkItemActivityStatusRef(
                          id = "st_open",
                          name = "Open",
                          group = "todo",
                        ),
                    ),
                  issueType = WorkItemActivityEntityRef(id = "it_bug", display = "Bug"),
                )
              ),
            sourceType = pending.command.sourceType,
            createdAt = pending.command.occurredAt,
          )
        }

        override suspend fun createAll(
          commands: List<CreateWorkItemActivityCommand<*>>
        ): List<WorkItemActivityRecord> {
          error("not used")
        }

        override suspend fun listByWorkItem(
          query: ListWorkItemActivitiesQuery
        ): WorkItemActivityListPage {
          error("not used")
        }
      }
    val handler = WorkItemActivityRecordRequestedHandler(activities, codec)
    val pipeline =
      WorkItemActivityConsumerPipeline(
        decoder = decoder,
        router = WorkItemActivityEventRouter(decoder, handler),
      )

    lateinit var container: ConcurrentMessageListenerContainer<String, String>

    beforeSpec {
      MessagingIntegrationFixtures.createTopics(DomainTopics.WORK_ITEM_ACTIVITY)
      val consumerProps =
        mapOf<String, Any>(
          ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
          ConsumerConfig.GROUP_ID_CONFIG to groupId,
          ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
          ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
          ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        )
      val factory = DefaultKafkaConsumerFactory<String, String>(consumerProps)
      val containerProps = ContainerProperties(DomainTopics.WORK_ITEM_ACTIVITY)
      containerProps.setMessageListener(
        MessageListener<String, String> { record -> pipeline.run(record) }
      )
      container = ConcurrentMessageListenerContainer(factory, containerProps)
      container.start()
    }

    afterSpec {
      container.stop()
    }

    "consumes work_item.activity.record_requested events from kafka" {
      processedCount.set(0)
      val payload =
        WorkItemActivityRecordRequestedEvent(
          activityId = UUID.randomUUID().toString(),
          activityApiId = PublicId.new("act").value,
          tenantId = UUID.randomUUID().toString(),
          projectId = UUID.randomUUID().toString(),
          workItemId = UUID.randomUUID().toString(),
          actorUserId = UUID.randomUUID().toString(),
          activityType = "work_item.created",
          occurredAt = OffsetDateTime.parse("2026-07-03T00:00:00Z").toString(),
          summary = "Created",
          payload =
            codec.encode(
              WorkItemActivitySpecs.Created,
              WorkItemCreatedPayload(
                status =
                  WorkItemActivityStatusSnapshot(
                    from = null,
                    to =
                      WorkItemActivityStatusRef(
                        id = "st_open",
                        name = "Open",
                        group = "todo",
                      ),
                  ),
                issueType = WorkItemActivityEntityRef(id = "it_bug", display = "Bug"),
              ),
            ),
          sourceType = "user",
        )
      val json =
        encoder.encode(
          WorkItemActivityDomainEvents.RecordRequested,
          payload,
          EventMetadata(tenantId = payload.tenantId),
        )

      KafkaTestSupport.publish(
        bootstrapServers = bootstrapServers,
        topic = DomainTopics.WORK_ITEM_ACTIVITY,
        key = PublicId.new("iss").value,
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
