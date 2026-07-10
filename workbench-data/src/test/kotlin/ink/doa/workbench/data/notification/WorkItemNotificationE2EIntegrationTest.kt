package ink.doa.workbench.data.notification

import ink.doa.workbench.core.notification.CreateNotificationCommand
import ink.doa.workbench.core.notification.NotificationChannel
import ink.doa.workbench.core.workitem.CreateWorkItemPersistenceCommand
import ink.doa.workbench.core.workitem.events.WorkItemMutationEvent
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.core.workitem.model.UpdateWorkItemCommand
import ink.doa.workbench.data.persistence.postgres.identity.UsersTable
import ink.doa.workbench.data.repository.notification.ExposedNotificationRepository
import ink.doa.workbench.data.support.seedWorkItemStack
import ink.doa.workbench.data.support.workItemRepository
import ink.doa.workbench.testsupport.postgres.MigrationSpec
import ink.doa.workbench.testsupport.postgres.WorkbenchPostgresTestSupport
import ink.doa.workbench.testsupport.postgres.jdbcTemplate
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.uuid.toKotlinUuid
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

@Tags("integration")
class WorkItemNotificationE2EIntegrationTest :
  StringSpec({
    val eventId = "evt_duplicate_delivery"
    val consumerName = "work-item-notifications"

    "duplicate delivery creates one notification and one processed record" {
      WorkbenchPostgresTestSupport.openDatabase(MigrationSpec.Full).use { lease ->
        val database = Database.connect(lease.jdbcTemplate().dataSource!!)
        val jdbc = lease.jdbcTemplate()
        val stack = seedWorkItemStack(database)
        val assigneeApiId =
          transaction(database) {
            UsersTable.selectAll()
              .where { UsersTable.id eq stack.actorId.toKotlinUuid() }
              .single()[UsersTable.apiId]
          }
        val repository = workItemRepository(database)
        val created = runBlocking {
          repository.create(
            CreateWorkItemPersistenceCommand(
              command =
                CreateWorkItemCommand(
                  tenantId = stack.tenantId,
                  projectId = stack.projectId,
                  issueTypeApiId = stack.issueType.apiId.value,
                  title = "Notify me",
                  description = null,
                  reporterId = stack.actorId,
                  actorUserId = stack.actorId,
                ),
              issueTypeId = stack.issueType.id,
              issueTypeConfigId = stack.config.config.id,
              initialStatusId = stack.todoStatus.id,
              parentIssueId = null,
              propertyValues = emptyList(),
            )
          )
        }
        runBlocking {
          repository.update(
            UpdateWorkItemCommand(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              workItemApiId = created.workItem.apiId.value,
              assigneeApiId = assigneeApiId,
              actorUserId = stack.actorId,
            ),
            propertyValues = emptyList(),
          )
        }
        val workItem = runBlocking {
          repository.findByApiId(
            stack.tenantId,
            stack.projectId,
            created.workItem.apiId.value,
          )!!
        }
        val payload = WorkItemMutationEvent.from(workItem)
        val notifications = ExposedNotificationRepository(database)
        repeat(3) {
          runBlocking {
            notifications.processIfUnprocessed(
              consumerName,
              eventId,
              CreateNotificationCommand(
                recipientUserId = stack.actorId,
                tenantId = stack.tenantId,
                projectId = stack.projectId,
                workItemId = workItem.id,
                sourceEventId = eventId,
                notificationType = "work_item.updated",
                title = "工作项 ${payload.key} 有更新",
                body = "工作项状态为 ${payload.statusGroup}",
                channels = setOf(NotificationChannel.IN_APP),
                payload =
                  buildJsonObject {
                    put("eventType", "work_item.updated")
                    put("workItemId", payload.workItemId)
                    put("key", payload.key)
                  },
              ),
            )
          }
        }

        jdbc.queryForObject(
          """
          SELECT count(*)
          FROM notifications
          WHERE source_event_id = ? AND recipient_user_id = ?
          """
            .trimIndent(),
          Long::class.java,
          eventId,
          stack.actorId,
        ) shouldBe 1L
        jdbc.queryForObject(
          """
          SELECT count(*)
          FROM processed_domain_events
          WHERE consumer_name = ? AND event_id = ?
          """
            .trimIndent(),
          Long::class.java,
          consumerName,
          eventId,
        ) shouldBe 1L
      }
    }
  })
