package ink.doa.workbench.data.repository.sprint

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.messaging.DomainEventEncoder
import ink.doa.workbench.core.messaging.DomainTopics
import ink.doa.workbench.core.sprint.SprintCloseFailureRequest
import ink.doa.workbench.core.sprint.SprintCloseSuccessRequest
import ink.doa.workbench.core.sprint.events.SprintDomainEvents
import ink.doa.workbench.core.sprint.model.SprintCloseDisposition
import ink.doa.workbench.core.sprint.model.SprintCloseOperationStatus
import ink.doa.workbench.core.sprint.model.SprintStatus
import ink.doa.workbench.data.messaging.ExposedDomainEventOutbox
import ink.doa.workbench.data.persistence.postgres.workitem.DomainOutboxTable
import ink.doa.workbench.data.persistence.postgres.workitem.SprintCloseOperationsTable
import ink.doa.workbench.data.persistence.postgres.workitem.SprintsTable
import ink.doa.workbench.data.support.seedUser
import ink.doa.workbench.data.support.seedWorkItemStack
import ink.doa.workbench.data.support.withPostgresDatabase
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

@Tags("integration")
class SprintCloseOperationOutboxIntegrationTest :
  StringSpec({
    "completeSucceeded closes sprint, marks operation, and enqueues sprint.closed" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val actorId = seedUser(database)
        val sprintId = UUID.randomUUID()
        val sprintApiId = PublicId.new("spr")
        val operationId = UUID.randomUUID()
        val operationApiId = PublicId.new("sop")
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        transaction(database) {
          SprintsTable.insert {
            it[id] = sprintId.toKotlinUuid()
            it[apiId] = sprintApiId.value
            it[tenantId] = stack.tenantId.toKotlinUuid()
            it[projectId] = stack.projectId.toKotlinUuid()
            it[name] = "Sprint"
            it[status] = SprintStatus.CLOSING.dbValue
            it[createdBy] = actorId.toKotlinUuid()
            it[createdAt] = now
            it[updatedAt] = now
          }
          SprintCloseOperationsTable.insert {
            it[id] = operationId.toKotlinUuid()
            it[apiId] = operationApiId.value
            it[tenantId] = stack.tenantId.toKotlinUuid()
            it[projectId] = stack.projectId.toKotlinUuid()
            it[SprintCloseOperationsTable.sprintId] = sprintId.toKotlinUuid()
            it[disposition] = SprintCloseDisposition.KEEP.name
            it[requestedBy] = actorId.toKotlinUuid()
            it[status] = SprintCloseOperationStatus.RUNNING.name
            it[totalItems] = 0
            it[processedItems] = 0
            it[failedItems] = 0
            it[createdAt] = now
          }
        }
        val repository =
          ExposedSprintCloseOperationRepository(
            database,
            ExposedDomainEventOutbox(database, DomainEventEncoder(Clock.systemUTC())),
          )

        runBlocking {
          repository.completeSucceeded(
            SprintCloseSuccessRequest(
              operationId = operationId,
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              sprintApiId = sprintApiId.value,
              operationApiId = operationApiId.value,
              closedAt = now,
              actorUserId = actorId,
            )
          )
        }

        transaction(database) {
          SprintsTable.selectAll()
            .where { SprintsTable.id eq sprintId.toKotlinUuid() }
            .single()[SprintsTable.status] shouldBe SprintStatus.CLOSED.dbValue
          SprintCloseOperationsTable.selectAll()
            .where { SprintCloseOperationsTable.id eq operationId.toKotlinUuid() }
            .single()[SprintCloseOperationsTable.status] shouldBe
            SprintCloseOperationStatus.SUCCEEDED.name
          DomainOutboxTable.selectAll()
            .where { DomainOutboxTable.eventType eq SprintDomainEvents.Closed.type }
            .count() shouldBe 1
          DomainOutboxTable.selectAll()
            .where { DomainOutboxTable.eventType eq SprintDomainEvents.Closed.type }
            .single()[DomainOutboxTable.topic] shouldBe DomainTopics.SPRINT
        }
      }
    }

    "completeFailed marks operation failed and enqueues sprint.close_failed" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val actorId = seedUser(database)
        val sprintId = UUID.randomUUID()
        val sprintApiId = PublicId.new("spr")
        val operationId = UUID.randomUUID()
        val operationApiId = PublicId.new("sop")
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        transaction(database) {
          SprintsTable.insert {
            it[id] = sprintId.toKotlinUuid()
            it[apiId] = sprintApiId.value
            it[tenantId] = stack.tenantId.toKotlinUuid()
            it[projectId] = stack.projectId.toKotlinUuid()
            it[name] = "Sprint"
            it[status] = SprintStatus.CLOSING.dbValue
            it[createdBy] = actorId.toKotlinUuid()
            it[createdAt] = now
            it[updatedAt] = now
          }
          SprintCloseOperationsTable.insert {
            it[id] = operationId.toKotlinUuid()
            it[apiId] = operationApiId.value
            it[tenantId] = stack.tenantId.toKotlinUuid()
            it[projectId] = stack.projectId.toKotlinUuid()
            it[SprintCloseOperationsTable.sprintId] = sprintId.toKotlinUuid()
            it[disposition] = SprintCloseDisposition.KEEP.name
            it[requestedBy] = actorId.toKotlinUuid()
            it[status] = SprintCloseOperationStatus.RUNNING.name
            it[totalItems] = 0
            it[processedItems] = 0
            it[failedItems] = 0
            it[createdAt] = now
          }
        }
        val repository =
          ExposedSprintCloseOperationRepository(
            database,
            ExposedDomainEventOutbox(database, DomainEventEncoder(Clock.systemUTC())),
          )

        runBlocking {
          repository.completeFailed(
            SprintCloseFailureRequest(
              operationId = operationId,
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              sprintApiId = sprintApiId.value,
              operationApiId = operationApiId.value,
              error = "batch failed",
              completedAt = now,
            )
          )
        }

        transaction(database) {
          SprintCloseOperationsTable.selectAll()
            .where { SprintCloseOperationsTable.id eq operationId.toKotlinUuid() }
            .single()[SprintCloseOperationsTable.status] shouldBe
            SprintCloseOperationStatus.FAILED.name
          DomainOutboxTable.selectAll()
            .where { DomainOutboxTable.eventType eq SprintDomainEvents.CloseFailed.type }
            .count() shouldBe 1
        }
      }
    }
  })
