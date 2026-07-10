package ink.doa.workbench.data.messaging

import ink.doa.workbench.core.messaging.DomainTopics
import ink.doa.workbench.core.messaging.EventMetadata
import ink.doa.workbench.core.port.messaging.DomainEventOutbox
import ink.doa.workbench.core.workitem.CreateWorkItemPersistenceCommand
import ink.doa.workbench.core.workitem.events.WorkItemDomainEvents
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.core.workitem.stream.WorkItemEventCodec
import ink.doa.workbench.data.persistence.postgres.workitem.DomainOutboxTable
import ink.doa.workbench.data.persistence.postgres.workitem.IssuesTable
import ink.doa.workbench.data.repository.workitem.ExposedWorkItemRepository
import ink.doa.workbench.data.repository.workitem.WorkItemEventFactory
import ink.doa.workbench.data.support.seedWorkItemStack
import ink.doa.workbench.data.support.withPostgresDatabase
import ink.doa.workbench.data.support.workItemRepository
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

@Tags("integration")
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
          outbox[DomainOutboxTable.status] shouldBe "PENDING"
          outbox[DomainOutboxTable.eventType] shouldBe WorkItemDomainEvents.Created.type
          outbox[DomainOutboxTable.topic] shouldBe DomainTopics.WORK_ITEM
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
  })
