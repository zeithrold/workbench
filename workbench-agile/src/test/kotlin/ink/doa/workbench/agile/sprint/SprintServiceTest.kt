package ink.doa.workbench.agile.sprint

import ink.doa.workbench.agile.project.ProjectOperationalGuard
import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.UserRepository
import ink.doa.workbench.core.identity.model.UserRecord
import ink.doa.workbench.core.project.model.ProjectRecord
import ink.doa.workbench.core.project.model.ProjectStatus
import ink.doa.workbench.core.sprint.SprintCloseOperationRepository
import ink.doa.workbench.core.sprint.SprintRepository
import ink.doa.workbench.core.sprint.model.ArchiveSprintCommand
import ink.doa.workbench.core.sprint.model.CloseSprintCommand
import ink.doa.workbench.core.sprint.model.CreateSprintCommand
import ink.doa.workbench.core.sprint.model.DeleteSprintCommand
import ink.doa.workbench.core.sprint.model.SprintCloseDisposition
import ink.doa.workbench.core.sprint.model.SprintCloseOperationRecord
import ink.doa.workbench.core.sprint.model.SprintCloseOperationStatus
import ink.doa.workbench.core.sprint.model.SprintRecord
import ink.doa.workbench.core.sprint.model.SprintStatus
import ink.doa.workbench.core.sprint.model.StartSprintCommand
import ink.doa.workbench.core.sprint.model.UpdateSprintCommand
import ink.doa.workbench.core.workitem.WorkItemRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class SprintServiceTest :
  FunSpec({
    val sprints = mockk<SprintRepository>()
    val users = mockk<UserRepository>()
    val projectOperationalGuard = mockk<ProjectOperationalGuard>()
    val closeOperations = mockk<SprintCloseOperationRepository>(relaxed = true)
    val workItems = mockk<WorkItemRepository>(relaxed = true)
    val clock = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC)
    val service =
      SprintService(
        sprints,
        users,
        projectOperationalGuard,
        closeOperations,
        workItems,
        clock,
      )

    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()
    val actorId = UUID.randomUUID()
    val sprintId = UUID.randomUUID()
    val now = OffsetDateTime.parse("2026-01-01T00:00:00Z")
    val operation =
      SprintCloseOperationRecord(
        id = UUID.randomUUID(),
        apiId = PublicId("sop_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        tenantId = tenantId,
        projectId = projectId,
        sprintId = sprintId,
        sprintApiId = PublicId("spr_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        targetSprintId = null,
        targetSprintApiId = null,
        disposition = SprintCloseDisposition.KEEP,
        requestedBy = actorId,
        status = SprintCloseOperationStatus.QUEUED,
        totalItems = 0,
        processedItems = 0,
        failedItems = 0,
        lastError = null,
        idempotencyKey = null,
        createdAt = now,
        startedAt = null,
        completedAt = null,
      )

    val plannedSprint =
      SprintRecord(
        id = sprintId,
        apiId = PublicId("spr_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        tenantId = tenantId,
        projectId = projectId,
        name = "Sprint 1",
        goal = "Ship it",
        status = SprintStatus.PLANNED,
        startAt = null,
        endAt = null,
        closedAt = null,
        createdBy = actorId,
        archivedAt = null,
        archivedBy = null,
        deletedAt = null,
        createdAt = now,
        updatedAt = now,
      )

    beforeTest {
      coEvery { projectOperationalGuard.ensureWritable(tenantId, projectId) } returns
        ProjectRecord(
          id = projectId,
          apiId = PublicId("prj_01JABCDEFGHJKMNPQRSTVWXYZ0"),
          tenantId = tenantId,
          identifier = "CORE",
          name = "Core",
          description = null,
          status = ProjectStatus.ACTIVE,
          leadUserId = actorId,
          createdBy = actorId,
        )
      coEvery { users.findById(actorId) } returns
        UserRecord(
          id = actorId,
          apiId = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ1"),
          displayName = "Ada",
          primaryEmail = "ada@example.test",
        )
      coEvery { sprints.findByApiId(tenantId, projectId, plannedSprint.apiId.value) } returns
        plannedSprint
    }

    test("list returns assembled sprint views") {
      coEvery { sprints.list(tenantId, projectId, SprintStatus.PLANNED) } returns
        listOf(plannedSprint)

      val views = service.list(tenantId, projectId, SprintStatus.PLANNED)

      views.single().name shouldBe "Sprint 1"
      views.single().createdBy?.displayName shouldBe "Ada"
    }

    test("create persists sprint when date range is valid") {
      val command =
        CreateSprintCommand(
          tenantId = tenantId,
          projectId = projectId,
          name = "Sprint 2",
          goal = "Finish",
          startAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
          endAt = OffsetDateTime.parse("2026-02-01T00:00:00Z"),
          createdBy = actorId,
        )
      coEvery { sprints.create(command) } returns plannedSprint.copy(name = "Sprint 2")

      service.create(command).name shouldBe "Sprint 2"
    }

    test("update allows goal changes on closed sprint") {
      val closed =
        plannedSprint.copy(status = SprintStatus.CLOSED, closedAt = OffsetDateTime.now(clock))
      val command =
        UpdateSprintCommand(
          tenantId = tenantId,
          projectId = projectId,
          sprintApiId = closed.apiId.value,
          goal = "Updated goal",
          actorUserId = actorId,
        )
      coEvery { sprints.findByApiId(tenantId, projectId, closed.apiId.value) } returns closed
      coEvery { sprints.update(command) } returns closed.copy(goal = "Updated goal")

      service.update(command).goal shouldBe "Updated goal"
    }

    test("update rejects startAt changes on active sprint") {
      val active = plannedSprint.copy(status = SprintStatus.ACTIVE)
      coEvery { sprints.findByApiId(tenantId, projectId, active.apiId.value) } returns active

      shouldThrow<InvalidRequestException> {
          service.update(
            UpdateSprintCommand(
              tenantId = tenantId,
              projectId = projectId,
              sprintApiId = active.apiId.value,
              startAt = OffsetDateTime.parse("2026-02-01T00:00:00Z"),
              actorUserId = actorId,
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.SPRINT_CLOSED_IMMUTABLE
    }

    test("update applies changes to planned sprint") {
      val command =
        UpdateSprintCommand(
          tenantId = tenantId,
          projectId = projectId,
          sprintApiId = plannedSprint.apiId.value,
          name = "Renamed",
          actorUserId = actorId,
        )
      coEvery { sprints.update(command) } returns plannedSprint.copy(name = "Renamed")

      service.update(command).name shouldBe "Renamed"
    }

    test("start uses existing startAt when already set") {
      val scheduled = plannedSprint.copy(startAt = OffsetDateTime.parse("2026-01-10T00:00:00Z"))
      coEvery { sprints.findByApiId(tenantId, projectId, scheduled.apiId.value) } returns scheduled
      coEvery { sprints.countActiveByProject(tenantId, projectId, sprintId) } returns 0
      coEvery {
        sprints.markActive(
          tenantId = tenantId,
          projectId = projectId,
          sprintApiId = scheduled.apiId.value,
          startAt = OffsetDateTime.parse("2026-01-10T00:00:00Z"),
          actorUserId = actorId,
        )
      } returns scheduled.copy(status = SprintStatus.ACTIVE)

      service
        .start(
          StartSprintCommand(
            tenantId = tenantId,
            projectId = projectId,
            sprintApiId = scheduled.apiId.value,
            actorUserId = actorId,
          )
        )
        .startAt shouldBe OffsetDateTime.parse("2026-01-10T00:00:00Z")
    }

    test("close queues an operation for an active sprint") {
      val active = plannedSprint.copy(status = SprintStatus.ACTIVE)
      coEvery { sprints.findByApiId(tenantId, projectId, active.apiId.value) } returns active
      coEvery {
        closeOperations.createAndMarkClosing(
          any(),
          any(),
          any(),
          any(),
          any(),
          any(),
          any(),
          any(),
          any(),
          any(),
        )
      } returns operation

      service
        .close(
          CloseSprintCommand(
            tenantId = tenantId,
            projectId = projectId,
            sprintApiId = active.apiId.value,
            actorUserId = actorId,
          )
        )
        .status shouldBe SprintCloseOperationStatus.QUEUED.name
    }

    test("closeOperation returns the stored operation") {
      coEvery {
        closeOperations.findByApiId(
          tenantId,
          projectId,
          plannedSprint.apiId.value,
          operation.apiId.value,
        )
      } returns operation

      service
        .closeOperation(tenantId, projectId, plannedSprint.apiId.value, operation.apiId.value)
        .id shouldBe operation.apiId.value
    }

    test("retryCloseOperation requeues a failed operation and publishes an event") {
      val failed =
        operation.copy(status = SprintCloseOperationStatus.FAILED, lastError = "batch failed")
      coEvery {
        closeOperations.findByApiId(
          tenantId,
          projectId,
          plannedSprint.apiId.value,
          operation.apiId.value,
        )
      } returns failed
      coEvery { closeOperations.markQueued(failed.id, any()) } returns true

      service
        .retryCloseOperation(
          tenantId,
          projectId,
          plannedSprint.apiId.value,
          operation.apiId.value,
        )
        .status shouldBe SprintCloseOperationStatus.QUEUED.name

      coVerify { closeOperations.markQueued(failed.id, any()) }
    }

    test("retryCloseOperation rejects a non-failed operation") {
      coEvery {
        closeOperations.findByApiId(
          tenantId,
          projectId,
          plannedSprint.apiId.value,
          operation.apiId.value,
        )
      } returns operation

      shouldThrow<InvalidRequestException> {
          service.retryCloseOperation(
            tenantId,
            projectId,
            plannedSprint.apiId.value,
            operation.apiId.value,
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.SPRINT_CLOSE_OPERATION_CONFLICT
    }

    test("retryCloseOperation rejects a missing operation") {
      coEvery {
        closeOperations.findByApiId(
          tenantId,
          projectId,
          plannedSprint.apiId.value,
          "sop_missing",
        )
      } returns null

      shouldThrow<ResourceNotFoundException> {
          service.retryCloseOperation(tenantId, projectId, plannedSprint.apiId.value, "sop_missing")
        }
        .errorCode shouldBe WorkbenchErrorCode.SPRINT_CLOSE_OPERATION_NOT_FOUND
    }

    test("archive delegates to repository") {
      val command =
        ArchiveSprintCommand(
          tenantId = tenantId,
          projectId = projectId,
          sprintApiId = plannedSprint.apiId.value,
          actorUserId = actorId,
        )
      coEvery { sprints.markArchived(command) } returns
        plannedSprint.copy(archivedAt = OffsetDateTime.now(clock), archivedBy = actorId)

      service.archive(command).id shouldBe plannedSprint.apiId.value
    }

    test("archive rejects a sprint that is already closing") {
      val command =
        ArchiveSprintCommand(
          tenantId = tenantId,
          projectId = projectId,
          sprintApiId = plannedSprint.apiId.value,
          actorUserId = actorId,
        )
      coEvery { sprints.findByApiId(tenantId, projectId, plannedSprint.apiId.value) } returns
        plannedSprint.copy(status = SprintStatus.CLOSING)

      shouldThrow<InvalidRequestException> { service.archive(command) }.errorCode shouldBe
        WorkbenchErrorCode.SPRINT_CLOSING
    }

    test("delete soft-deletes sprint") {
      val command =
        DeleteSprintCommand(
          tenantId = tenantId,
          projectId = projectId,
          sprintApiId = plannedSprint.apiId.value,
          actorUserId = actorId,
        )
      coEvery { sprints.softDelete(command) } returns true

      service.delete(command)
    }

    test("get assembles sprint without creator when user is missing") {
      val anonymous = plannedSprint.copy(createdBy = null)
      coEvery { sprints.findByApiId(tenantId, projectId, anonymous.apiId.value) } returns anonymous

      service.get(tenantId, projectId, anonymous.apiId.value).createdBy shouldBe null
    }

    test("start activates sprint when no other active sprint exists") {
      coEvery { sprints.countActiveByProject(tenantId, projectId, sprintId) } returns 0
      coEvery {
        sprints.markActive(
          tenantId = tenantId,
          projectId = projectId,
          sprintApiId = plannedSprint.apiId.value,
          startAt = OffsetDateTime.parse("2026-01-15T10:00:00Z"),
          actorUserId = actorId,
        )
      } returns plannedSprint.copy(status = SprintStatus.ACTIVE)

      val view =
        service.start(
          StartSprintCommand(
            tenantId = tenantId,
            projectId = projectId,
            sprintApiId = plannedSprint.apiId.value,
            actorUserId = actorId,
          )
        )

      view.status shouldBe SprintStatus.ACTIVE
    }

    test("start rejects when another active sprint exists") {
      coEvery { sprints.countActiveByProject(tenantId, projectId, sprintId) } returns 1

      shouldThrow<InvalidRequestException> {
          service.start(
            StartSprintCommand(
              tenantId = tenantId,
              projectId = projectId,
              sprintApiId = plannedSprint.apiId.value,
              actorUserId = actorId,
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.SPRINT_ACTIVE_CONFLICT
    }

    test("update rejects structural changes on closed sprint") {
      val closed =
        plannedSprint.copy(status = SprintStatus.CLOSED, closedAt = OffsetDateTime.now(clock))
      coEvery { sprints.findByApiId(tenantId, projectId, closed.apiId.value) } returns closed

      shouldThrow<InvalidRequestException> {
          service.update(
            UpdateSprintCommand(
              tenantId = tenantId,
              projectId = projectId,
              sprintApiId = closed.apiId.value,
              name = "Renamed",
              actorUserId = actorId,
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.SPRINT_CLOSED_IMMUTABLE
    }

    test("close requires active sprint") {
      shouldThrow<InvalidRequestException> {
          service.close(
            CloseSprintCommand(
              tenantId = tenantId,
              projectId = projectId,
              sprintApiId = plannedSprint.apiId.value,
              actorUserId = actorId,
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.SPRINT_STATUS_INVALID_TRANSITION
    }

    test("create validates date range") {
      shouldThrow<InvalidRequestException> {
          service.create(
            CreateSprintCommand(
              tenantId = tenantId,
              projectId = projectId,
              name = "Bad dates",
              goal = null,
              startAt = OffsetDateTime.parse("2026-02-01T00:00:00Z"),
              endAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
              createdBy = actorId,
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.SPRINT_DATE_RANGE_INVALID
    }

    test("get throws when sprint is missing") {
      coEvery { sprints.findByApiId(tenantId, projectId, "spr_missing") } returns null

      shouldThrow<ResourceNotFoundException> {
          service.get(tenantId, projectId, "spr_missing")
        }
        .errorCode shouldBe WorkbenchErrorCode.RESOURCE_SPRINT_NOT_FOUND
    }

    test("close operation returns stored operation view") {
      coEvery {
        closeOperations.findByApiId(
          tenantId,
          projectId,
          plannedSprint.apiId.value,
          operation.apiId.value,
        )
      } returns operation

      service
        .closeOperation(tenantId, projectId, plannedSprint.apiId.value, operation.apiId.value)
        .id shouldBe operation.apiId.value
    }

    test("close returns existing operation for idempotency key") {
      val active = plannedSprint.copy(status = SprintStatus.ACTIVE)
      coEvery { sprints.findByApiId(tenantId, projectId, active.apiId.value) } returns active
      coEvery {
        closeOperations.findByIdempotencyKey(tenantId, projectId, sprintId, "idem-1")
      } returns operation

      service
        .close(
          CloseSprintCommand(
            tenantId = tenantId,
            projectId = projectId,
            sprintApiId = active.apiId.value,
            actorUserId = actorId,
            idempotencyKey = "idem-1",
          )
        )
        .id shouldBe operation.apiId.value
    }

    test("close operation throws when operation is missing") {
      coEvery {
        closeOperations.findByApiId(tenantId, projectId, plannedSprint.apiId.value, "sop_missing")
      } returns null

      shouldThrow<ResourceNotFoundException> {
          service.closeOperation(tenantId, projectId, plannedSprint.apiId.value, "sop_missing")
        }
        .errorCode shouldBe WorkbenchErrorCode.SPRINT_CLOSE_OPERATION_NOT_FOUND
    }

    test("close rejects sprint already closing") {
      val closing = plannedSprint.copy(status = SprintStatus.CLOSING)
      coEvery { sprints.findByApiId(tenantId, projectId, closing.apiId.value) } returns closing

      shouldThrow<InvalidRequestException> {
          service.close(
            CloseSprintCommand(
              tenantId = tenantId,
              projectId = projectId,
              sprintApiId = closing.apiId.value,
              actorUserId = actorId,
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.SPRINT_CLOSE_OPERATION_CONFLICT
    }

    test("retry close operation re-enqueues failed operation") {
      val failed = operation.copy(status = SprintCloseOperationStatus.FAILED, lastError = "boom")
      coEvery {
        closeOperations.findByApiId(
          tenantId,
          projectId,
          plannedSprint.apiId.value,
          operation.apiId.value,
        )
      } returns failed
      coEvery { closeOperations.retryAndEnqueue(any()) } returns
        failed.copy(status = SprintCloseOperationStatus.QUEUED, lastError = null)

      service
        .retryCloseOperation(
          tenantId,
          projectId,
          plannedSprint.apiId.value,
          operation.apiId.value,
        )
        .status shouldBe SprintCloseOperationStatus.QUEUED.name
    }

    test("retry close operation rejects non-failed operations") {
      coEvery {
        closeOperations.findByApiId(
          tenantId,
          projectId,
          plannedSprint.apiId.value,
          operation.apiId.value,
        )
      } returns operation

      shouldThrow<InvalidRequestException> {
          service.retryCloseOperation(
            tenantId,
            projectId,
            plannedSprint.apiId.value,
            operation.apiId.value,
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.SPRINT_CLOSE_OPERATION_CONFLICT
    }

    test("retry close operation throws when operation is missing") {
      coEvery {
        closeOperations.findByApiId(
          tenantId,
          projectId,
          plannedSprint.apiId.value,
          "sop_missing",
        )
      } returns null

      shouldThrow<ResourceNotFoundException> {
          service.retryCloseOperation(
            tenantId,
            projectId,
            plannedSprint.apiId.value,
            "sop_missing",
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.SPRINT_CLOSE_OPERATION_NOT_FOUND
    }
  })
