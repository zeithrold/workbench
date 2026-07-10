package ink.doa.workbench.data.repository.sprint

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.messaging.DomainEventEncoder
import ink.doa.workbench.core.sprint.SprintCloseRetryRequest
import ink.doa.workbench.core.sprint.events.SprintCloseRequestedEvent
import ink.doa.workbench.core.sprint.events.SprintDomainEvents
import ink.doa.workbench.core.sprint.model.CreateSprintCommand
import ink.doa.workbench.core.sprint.model.SprintCloseDisposition
import ink.doa.workbench.core.sprint.model.SprintCloseOperationStatus
import ink.doa.workbench.core.sprint.model.SprintStatus
import ink.doa.workbench.data.messaging.ExposedDomainEventOutbox
import ink.doa.workbench.data.persistence.postgres.workitem.DomainOutboxTable
import ink.doa.workbench.data.persistence.postgres.workitem.SprintsTable
import ink.doa.workbench.data.support.seedWorkItemStack
import ink.doa.workbench.data.support.withPostgresDatabase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.uuid.toKotlinUuid
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

@Tags("integration")
class ExposedSprintCloseOperationRepositoryIntegrationTest :
  StringSpec({
    fun repository(database: org.jetbrains.exposed.v1.jdbc.Database) =
      ExposedSprintCloseOperationRepository(
        database,
        ExposedDomainEventOutbox(database, DomainEventEncoder(Clock.systemUTC())),
      )

    "createAndMarkClosing marks sprint closing and enqueues close_requested" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val sprints = ExposedSprintRepository(database)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val sprint =
          sprints.create(
            CreateSprintCommand(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              name = "Sprint A",
              goal = null,
              startAt = now,
              endAt = now.plusDays(14),
              createdBy = stack.actorId,
            )
          )
        val active =
          sprints.markActive(
            tenantId = stack.tenantId,
            projectId = stack.projectId,
            sprintApiId = sprint.apiId.value,
            startAt = now,
            actorUserId = stack.actorId,
          )
        val repo = repository(database)

        val operation = runBlocking {
          repo.createAndMarkClosing(
            tenantId = stack.tenantId,
            projectId = stack.projectId,
            sprintId = active.id,
            sprintApiId = active.apiId.value,
            targetSprintId = null,
            targetSprintApiId = null,
            disposition = SprintCloseDisposition.KEEP,
            requestedBy = stack.actorId,
            idempotencyKey = "close-idem-1",
            createdAt = now,
          )
        }

        operation.status shouldBe SprintCloseOperationStatus.QUEUED
        operation.idempotencyKey shouldBe "close-idem-1"
        transaction(database) {
          SprintsTable.selectAll()
            .where { SprintsTable.id eq active.id.toKotlinUuid() }
            .single()[SprintsTable.status] shouldBe SprintStatus.CLOSING.dbValue
          DomainOutboxTable.selectAll()
            .where { DomainOutboxTable.eventType eq SprintDomainEvents.CloseRequested.type }
            .count() shouldBe 1
        }
      }
    }

    "findByApiId and findByIdempotencyKey locate operations" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val sprints = ExposedSprintRepository(database)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val sprint =
          sprints.markActive(
            tenantId = stack.tenantId,
            projectId = stack.projectId,
            sprintApiId =
              sprints
                .create(
                  CreateSprintCommand(
                    tenantId = stack.tenantId,
                    projectId = stack.projectId,
                    name = "Lookup Sprint",
                    goal = null,
                    startAt = now,
                    endAt = now.plusDays(7),
                    createdBy = stack.actorId,
                  )
                )
                .apiId
                .value,
            startAt = now,
            actorUserId = stack.actorId,
          )
        val repo = repository(database)
        val created = runBlocking {
          repo.createAndMarkClosing(
            tenantId = stack.tenantId,
            projectId = stack.projectId,
            sprintId = sprint.id,
            sprintApiId = sprint.apiId.value,
            targetSprintId = null,
            targetSprintApiId = null,
            disposition = SprintCloseDisposition.NEXT_SPRINT,
            requestedBy = stack.actorId,
            idempotencyKey = "lookup-key",
            createdAt = now,
          )
        }

        runBlocking {
          repo.findByApiId(
            tenantId = stack.tenantId,
            projectId = stack.projectId,
            sprintApiId = sprint.apiId.value,
            operationApiId = created.apiId.value,
          ) shouldBe created
          repo.findByIdempotencyKey(
            tenantId = stack.tenantId,
            projectId = stack.projectId,
            sprintId = sprint.id,
            idempotencyKey = "lookup-key",
          ) shouldBe created
          repo
            .findByApiId(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              sprintApiId = sprint.apiId.value,
              operationApiId = "sop_missing",
            )
            .shouldBeNull()
        }
      }
    }

    "markRunning markQueued updateProgress setTotalItems and markCompleted" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val sprints = ExposedSprintRepository(database)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val sprint =
          sprints.markActive(
            tenantId = stack.tenantId,
            projectId = stack.projectId,
            sprintApiId =
              sprints
                .create(
                  CreateSprintCommand(
                    tenantId = stack.tenantId,
                    projectId = stack.projectId,
                    name = "Progress Sprint",
                    goal = null,
                    startAt = now,
                    endAt = now.plusDays(7),
                    createdBy = stack.actorId,
                  )
                )
                .apiId
                .value,
            startAt = now,
            actorUserId = stack.actorId,
          )
        val repo = repository(database)
        val operation = runBlocking {
          repo.createAndMarkClosing(
            tenantId = stack.tenantId,
            projectId = stack.projectId,
            sprintId = sprint.id,
            sprintApiId = sprint.apiId.value,
            targetSprintId = null,
            targetSprintApiId = null,
            disposition = SprintCloseDisposition.KEEP,
            requestedBy = stack.actorId,
            idempotencyKey = null,
            createdAt = now,
          )
        }

        runBlocking {
          repo.markRunning(operation.id, now.plusMinutes(1)) shouldBe true
          repo.setTotalItems(operation.id, 10) shouldBe true
          repo.updateProgress(
            operation.id,
            processedItems = 4,
            failedItems = 1,
            updatedAt = now,
          ) shouldBe true
          repo.markCompleted(
            operation.id,
            SprintCloseOperationStatus.FAILED,
            now.plusMinutes(5),
            "worker error",
          ) shouldBe true
          repo.markQueued(operation.id, now.plusMinutes(6)) shouldBe true
        }

        runBlocking {
          val requeued =
            repo.findByApiId(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              sprintApiId = sprint.apiId.value,
              operationApiId = operation.apiId.value,
            )
          requeued.shouldNotBeNull()
          requeued.status shouldBe SprintCloseOperationStatus.QUEUED
          requeued.totalItems shouldBe 10
          requeued.processedItems shouldBe 4
          requeued.failedItems shouldBe 1
          requeued.lastError.shouldBeNull()
          requeued.completedAt.shouldBeNull()
        }
      }
    }

    "retryAndEnqueue requeues failed operation and appends close_requested" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val sprints = ExposedSprintRepository(database)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val sprint =
          sprints.markActive(
            tenantId = stack.tenantId,
            projectId = stack.projectId,
            sprintApiId =
              sprints
                .create(
                  CreateSprintCommand(
                    tenantId = stack.tenantId,
                    projectId = stack.projectId,
                    name = "Retry Sprint",
                    goal = null,
                    startAt = now,
                    endAt = now.plusDays(7),
                    createdBy = stack.actorId,
                  )
                )
                .apiId
                .value,
            startAt = now,
            actorUserId = stack.actorId,
          )
        val repo = repository(database)
        val operation = runBlocking {
          repo.createAndMarkClosing(
            tenantId = stack.tenantId,
            projectId = stack.projectId,
            sprintId = sprint.id,
            sprintApiId = sprint.apiId.value,
            targetSprintId = null,
            targetSprintApiId = null,
            disposition = SprintCloseDisposition.KEEP,
            requestedBy = stack.actorId,
            idempotencyKey = "retry-key",
            createdAt = now,
          )
        }
        runBlocking {
          repo.markRunning(operation.id, now)
          repo.markCompleted(
            operation.id,
            SprintCloseOperationStatus.FAILED,
            now.plusMinutes(1),
            "transient",
          )
        }

        val payload =
          SprintCloseRequestedEvent(
            tenantId = stack.tenantId.toString(),
            projectId = stack.projectId.toString(),
            sprintId = sprint.apiId.value,
            operationId = operation.apiId.value,
            requestedBy = stack.actorId.toString(),
          )
        val retried = runBlocking {
          repo.retryAndEnqueue(
            SprintCloseRetryRequest(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              sprintApiId = sprint.apiId.value,
              operationApiId = operation.apiId.value,
              payload = payload,
              metadataTenantId = stack.tenantId.toString(),
            )
          )
        }

        retried.status shouldBe SprintCloseOperationStatus.QUEUED
        transaction(database) {
          DomainOutboxTable.selectAll()
            .where { DomainOutboxTable.eventType eq SprintDomainEvents.CloseRequested.type }
            .count() shouldBe 2
        }
      }
    }

    "retryAndEnqueue rejects non-failed operations and missing records" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val repo = repository(database)
        val payload =
          SprintCloseRequestedEvent(
            tenantId = stack.tenantId.toString(),
            projectId = stack.projectId.toString(),
            sprintId = "spr_missing",
            operationId = "sop_missing",
            requestedBy = stack.actorId.toString(),
          )

        shouldThrow<ResourceNotFoundException> {
          runBlocking {
            repo.retryAndEnqueue(
              SprintCloseRetryRequest(
                tenantId = stack.tenantId,
                projectId = stack.projectId,
                sprintApiId = "spr_missing",
                operationApiId = "sop_missing",
                payload = payload,
                metadataTenantId = stack.tenantId.toString(),
              )
            )
          }
        }

        val sprints = ExposedSprintRepository(database)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val sprint =
          sprints.markActive(
            tenantId = stack.tenantId,
            projectId = stack.projectId,
            sprintApiId =
              sprints
                .create(
                  CreateSprintCommand(
                    tenantId = stack.tenantId,
                    projectId = stack.projectId,
                    name = "Conflict Sprint",
                    goal = null,
                    startAt = now,
                    endAt = now.plusDays(7),
                    createdBy = stack.actorId,
                  )
                )
                .apiId
                .value,
            startAt = now,
            actorUserId = stack.actorId,
          )
        val operation = runBlocking {
          repo.createAndMarkClosing(
            tenantId = stack.tenantId,
            projectId = stack.projectId,
            sprintId = sprint.id,
            sprintApiId = sprint.apiId.value,
            targetSprintId = null,
            targetSprintApiId = null,
            disposition = SprintCloseDisposition.KEEP,
            requestedBy = stack.actorId,
            idempotencyKey = null,
            createdAt = now,
          )
        }

        shouldThrow<InvalidRequestException> {
          runBlocking {
            repo.retryAndEnqueue(
              SprintCloseRetryRequest(
                tenantId = stack.tenantId,
                projectId = stack.projectId,
                sprintApiId = sprint.apiId.value,
                operationApiId = operation.apiId.value,
                payload = payload,
                metadataTenantId = stack.tenantId.toString(),
              )
            )
          }
        }
      }
    }

    "createAndMarkClosing rejects sprint that is not active" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val sprints = ExposedSprintRepository(database)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val sprint =
          sprints.create(
            CreateSprintCommand(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              name = "Planned Sprint",
              goal = null,
              startAt = now,
              endAt = now.plusDays(7),
              createdBy = stack.actorId,
            )
          )
        val repo = repository(database)

        shouldThrow<IllegalArgumentException> {
          runBlocking {
            repo.createAndMarkClosing(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              sprintId = sprint.id,
              sprintApiId = sprint.apiId.value,
              targetSprintId = null,
              targetSprintApiId = null,
              disposition = SprintCloseDisposition.KEEP,
              requestedBy = stack.actorId,
              idempotencyKey = null,
              createdAt = now,
            )
          }
        }
      }
    }
  })
