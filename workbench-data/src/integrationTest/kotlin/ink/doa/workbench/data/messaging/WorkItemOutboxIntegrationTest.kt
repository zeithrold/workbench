package ink.doa.workbench.data.messaging

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.messaging.DomainTopics
import ink.doa.workbench.core.messaging.EventMetadata
import ink.doa.workbench.core.port.messaging.DomainEventOutbox
import ink.doa.workbench.core.sprint.model.SprintCloseDisposition
import ink.doa.workbench.core.workitem.CreateWorkItemPersistenceCommand
import ink.doa.workbench.core.workitem.ReassignSprintBatchCommand
import ink.doa.workbench.core.workitem.events.WorkItemDomainEvents
import ink.doa.workbench.core.workitem.events.WorkItemSprintDomainEvents
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.core.workitem.stream.WorkItemEventCodec
import ink.doa.workbench.data.persistence.postgres.workitem.DomainOutboxTable
import ink.doa.workbench.data.persistence.postgres.workitem.IssuesTable
import ink.doa.workbench.data.persistence.postgres.workitem.SprintsTable
import ink.doa.workbench.data.repository.workitem.ExposedWorkItemRepository
import ink.doa.workbench.data.repository.workitem.WorkItemEventFactory
import ink.doa.workbench.data.support.seedWorkItemStack
import ink.doa.workbench.data.support.withPostgresDatabase
import ink.doa.workbench.data.support.workItemRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class WorkItemOutboxIntegrationTest :
  StringSpec({
    "work item mutation and outbox row commit in the same transaction" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val repository = workItemRepository(database)

        runBlocking {
          repository.create(
            CreateWorkItemPersistenceCommand(
              command =
                CreateWorkItemCommand(
                  tenantId = stack.tenantId,
                  projectId = stack.projectId,
                  issueTypeApiId = stack.issueType.apiId.value,
                  title = "Outbox task",
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

        transaction(database) {
          val outbox =
            DomainOutboxTable.selectAll()
              .where { DomainOutboxTable.eventType eq WorkItemDomainEvents.Created.type }
              .orderBy(DomainOutboxTable.createdAt to SortOrder.ASC)
              .last()
          outbox[DomainOutboxTable.eventType] shouldBe WorkItemDomainEvents.Created.type
          outbox[DomainOutboxTable.topic] shouldBe DomainTopics.WORK_ITEM
          outbox[DomainOutboxTable.retentionUntil] shouldBe
            (outbox[DomainOutboxTable.createdAt].plusDays(30))
        }
      }
    }

    "failed work item mutation rolls back outbox row" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val failingOutbox =
          object : DomainEventOutbox {
            override fun <T : Any> append(
              spec: ink.doa.workbench.core.messaging.DomainEventSpec<T>,
              key: String,
              payload: T,
              metadata: EventMetadata,
            ) {
              error("outbox write failed")
            }
          }
        val failingRepository =
          ExposedWorkItemRepository(
            database,
            WorkItemEventFactory(),
            WorkItemEventCodec(),
            WorkItemOutboxAppender(failingOutbox),
          )

        val before = transaction(database) { DomainOutboxTable.selectAll().count() }

        runCatching {
            runBlocking {
              failingRepository.create(
                CreateWorkItemPersistenceCommand(
                  command =
                    CreateWorkItemCommand(
                      tenantId = stack.tenantId,
                      projectId = stack.projectId,
                      issueTypeApiId = stack.issueType.apiId.value,
                      title = "Should rollback",
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
          }
          .exceptionOrNull()
          .shouldNotBeNull()

        transaction(database) {
          DomainOutboxTable.selectAll().count() shouldBe before
          IssuesTable.selectAll().where { IssuesTable.title eq "Should rollback" }.count() shouldBe
            0
        }
      }
    }

    "reassignSprintBatch moves work items and enqueues sprint_changed" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val repository = workItemRepository(database)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val sourceSprintId = UUID.randomUUID()
        val sourceSprintApiId = PublicId.new("spr").value
        val targetSprintId = UUID.randomUUID()
        val targetSprintApiId = PublicId.new("spr").value
        transaction(database) {
          SprintsTable.insert {
            it[id] = sourceSprintId.toKotlinUuid()
            it[apiId] = sourceSprintApiId
            it[tenantId] = stack.tenantId.toKotlinUuid()
            it[projectId] = stack.projectId.toKotlinUuid()
            it[name] = "Source Sprint"
            it[status] = "active"
            it[createdAt] = now
            it[updatedAt] = now
          }
          SprintsTable.insert {
            it[id] = targetSprintId.toKotlinUuid()
            it[apiId] = targetSprintApiId
            it[tenantId] = stack.tenantId.toKotlinUuid()
            it[projectId] = stack.projectId.toKotlinUuid()
            it[name] = "Target Sprint"
            it[status] = "planned"
            it[createdAt] = now
            it[updatedAt] = now
          }
        }

        runBlocking {
          repository.create(
            CreateWorkItemPersistenceCommand(
              command =
                CreateWorkItemCommand(
                  tenantId = stack.tenantId,
                  projectId = stack.projectId,
                  issueTypeApiId = stack.issueType.apiId.value,
                  title = "Sprint item",
                  description = null,
                  reporterId = stack.actorId,
                  actorUserId = stack.actorId,
                  sprintApiId = sourceSprintApiId,
                ),
              issueTypeId = stack.issueType.id,
              issueTypeConfigId = stack.config.config.id,
              initialStatusId = stack.todoStatus.id,
              parentIssueId = null,
              propertyValues = emptyList(),
            )
          )
        }

        val result = runBlocking {
          repository.reassignSprintBatch(
            ReassignSprintBatchCommand(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              sourceSprintId = sourceSprintId,
              targetSprintId = targetSprintId,
              disposition = SprintCloseDisposition.NEXT_SPRINT,
              actorUserId = stack.actorId,
              operationId = "sop_batch",
              limit = 10,
            )
          )
        }

        result.processedItems shouldBe 1
        result.remainingItems shouldBe 0
        result.changedItems.single().sprintApiId?.value shouldBe targetSprintApiId

        transaction(database) {
          DomainOutboxTable.selectAll()
            .where { DomainOutboxTable.eventType eq WorkItemSprintDomainEvents.SprintChanged.type }
            .count() shouldBe 1
        }

        runBlocking {
          repository.countUnfinishedBySprint(
            stack.tenantId,
            stack.projectId,
            targetSprintId,
          ) shouldBe 1
          repository.countUnfinishedBySprint(
            stack.tenantId,
            stack.projectId,
            sourceSprintId,
          ) shouldBe 0
        }
      }
    }
  })
