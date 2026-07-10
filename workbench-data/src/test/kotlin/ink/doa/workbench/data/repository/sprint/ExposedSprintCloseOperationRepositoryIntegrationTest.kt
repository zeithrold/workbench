package ink.doa.workbench.data.repository.sprint

import ink.doa.workbench.core.messaging.DomainEventEncoder
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

    "createAndMarkClosing changes sprint status and appends outbox event" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val sprintRepository = ExposedSprintRepository(database)
        val sprint =
          sprintRepository.create(
            CreateSprintCommand(
              stack.tenantId,
              stack.projectId,
              "Sprint",
              null,
              now,
              now.plusDays(7),
              stack.actorId,
            )
          )
        val active =
          sprintRepository.markActive(
            stack.tenantId,
            stack.projectId,
            sprint.apiId.value,
            now,
            stack.actorId,
          )
        val operation =
          repository(database)
            .createAndMarkClosing(
              stack.tenantId,
              stack.projectId,
              active.id,
              active.apiId.value,
              null,
              null,
              SprintCloseDisposition.KEEP,
              stack.actorId,
              "close-key",
              now,
            )
        operation.status shouldBe SprintCloseOperationStatus.QUEUED
        transaction(database) {
          SprintsTable.selectAll()
            .where { SprintsTable.id eq active.id.toKotlinUuid() }
            .single()[SprintsTable.status] shouldBe SprintStatus.CLOSING.dbValue
          DomainOutboxTable.selectAll()
            .where { DomainOutboxTable.eventType eq "sprint.close_requested" }
            .count() shouldBe 1
        }
      }
    }

    "finders and progress mutations round trip all operation state" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val sprintRepository = ExposedSprintRepository(database)
        val sprint =
          sprintRepository.create(
            CreateSprintCommand(
              stack.tenantId,
              stack.projectId,
              "Sprint",
              null,
              now,
              now.plusDays(7),
              stack.actorId,
            )
          )
        val active =
          sprintRepository.markActive(
            stack.tenantId,
            stack.projectId,
            sprint.apiId.value,
            now,
            stack.actorId,
          )
        val repo = repository(database)
        val operation =
          repo.createAndMarkClosing(
            stack.tenantId,
            stack.projectId,
            active.id,
            active.apiId.value,
            null,
            null,
            SprintCloseDisposition.KEEP,
            stack.actorId,
            "lookup-key",
            now,
          )
        repo.findByApiId(
          stack.tenantId,
          stack.projectId,
          active.apiId.value,
          operation.apiId.value,
        ) shouldBe operation
        repo.findByIdempotencyKey(stack.tenantId, stack.projectId, active.id, "lookup-key") shouldBe
          operation
        repo
          .findByApiId(stack.tenantId, stack.projectId, active.apiId.value, "sop_missing")
          .shouldBeNull()
        repo.markRunning(operation.id, now) shouldBe true
        repo.setTotalItems(operation.id, 10) shouldBe true
        repo.updateProgress(operation.id, 4, 1, now) shouldBe true
        repo.markCompleted(
          operation.id,
          SprintCloseOperationStatus.FAILED,
          now.plusMinutes(1),
          "failed",
        ) shouldBe true
        repo.markQueued(operation.id, now.plusMinutes(2)) shouldBe true
        val updated =
          repo
            .findByApiId(stack.tenantId, stack.projectId, active.apiId.value, operation.apiId.value)
            .shouldNotBeNull()
        updated.status shouldBe SprintCloseOperationStatus.QUEUED
        updated.totalItems shouldBe 10
        updated.processedItems shouldBe 4
        updated.failedItems shouldBe 1
        updated.lastError.shouldBeNull()
        updated.completedAt.shouldBeNull()
      }
    }

    "createAndMarkClosing rejects a sprint that is not active" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val sprint =
          ExposedSprintRepository(database)
            .create(
              CreateSprintCommand(
                stack.tenantId,
                stack.projectId,
                "Planned",
                null,
                now,
                now.plusDays(7),
                stack.actorId,
              )
            )
        shouldThrow<IllegalArgumentException> {
          repository(database)
            .createAndMarkClosing(
              stack.tenantId,
              stack.projectId,
              sprint.id,
              sprint.apiId.value,
              null,
              null,
              SprintCloseDisposition.KEEP,
              stack.actorId,
              null,
              now,
            )
        }
      }
    }
  })
