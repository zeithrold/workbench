package one.ztd.workbench.data.repository.sprint

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import one.ztd.workbench.agile.sprint.model.ArchiveSprintCommand
import one.ztd.workbench.agile.sprint.model.CreateSprintCommand
import one.ztd.workbench.agile.sprint.model.DeleteSprintCommand
import one.ztd.workbench.agile.sprint.model.SprintStatus
import one.ztd.workbench.agile.sprint.model.UpdateSprintCommand
import one.ztd.workbench.data.support.seedWorkItemStack
import one.ztd.workbench.data.support.withPostgresDatabase
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException

class ExposedSprintRepositoryIntegrationTest :
  StringSpec({
    "creates lists updates starts closes archives and deletes sprints" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val sprints = ExposedSprintRepository(database)

        val created =
          sprints.create(
            CreateSprintCommand(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              name = "Sprint 1",
              goal = "Deliver MVP",
              startAt = OffsetDateTime.parse("2026-02-01T00:00:00Z"),
              endAt = OffsetDateTime.parse("2026-02-14T00:00:00Z"),
              createdBy = stack.actorId,
            )
          )

        created.status shouldBe SprintStatus.PLANNED
        sprints.list(stack.tenantId, stack.projectId).shouldHaveSize(1)

        val updated =
          sprints.update(
            UpdateSprintCommand(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              sprintApiId = created.apiId.value,
              name = "Sprint 1.1",
              actorUserId = stack.actorId,
            )
          )
        updated.name shouldBe "Sprint 1.1"

        val active =
          sprints.markActive(
            tenantId = stack.tenantId,
            projectId = stack.projectId,
            sprintApiId = created.apiId.value,
            startAt = OffsetDateTime.parse("2026-02-01T00:00:00Z"),
            actorUserId = stack.actorId,
          )
        active.status shouldBe SprintStatus.ACTIVE
        sprints.countActiveByProject(stack.tenantId, stack.projectId) shouldBe 1

        val closed =
          sprints.markClosed(
            tenantId = stack.tenantId,
            projectId = stack.projectId,
            sprintApiId = created.apiId.value,
            closedAt = OffsetDateTime.parse("2026-02-14T12:00:00Z"),
            actorUserId = stack.actorId,
          )
        closed.status shouldBe SprintStatus.CLOSED
        closed.closedAt shouldBe OffsetDateTime.parse("2026-02-14T12:00:00Z")

        sprints.markArchived(
          ArchiveSprintCommand(
            tenantId = stack.tenantId,
            projectId = stack.projectId,
            sprintApiId = created.apiId.value,
            actorUserId = stack.actorId,
          )
        )
        sprints.findByApiId(stack.tenantId, stack.projectId, created.apiId.value).shouldBeNull()
      }
    }

    "softDelete hides sprint from active queries" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val sprints = ExposedSprintRepository(database)
        val created =
          sprints.create(
            CreateSprintCommand(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              name = "Delete me",
              goal = null,
              startAt = null,
              endAt = null,
              createdBy = stack.actorId,
            )
          )

        sprints.softDelete(
          DeleteSprintCommand(
            tenantId = stack.tenantId,
            projectId = stack.projectId,
            sprintApiId = created.apiId.value,
            actorUserId = stack.actorId,
          )
        ) shouldBe true
        sprints.findByApiId(stack.tenantId, stack.projectId, created.apiId.value).shouldBeNull()
      }
    }

    "filters sprints by status" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val sprints = ExposedSprintRepository(database)
        val sprint =
          sprints.create(
            CreateSprintCommand(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              name = "Planned sprint",
              goal = null,
              startAt = null,
              endAt = null,
              createdBy = stack.actorId,
            )
          )

        sprints.list(stack.tenantId, stack.projectId, SprintStatus.PLANNED).single().id shouldBe
          sprint.id
        sprints.list(stack.tenantId, stack.projectId, SprintStatus.ACTIVE).shouldHaveSize(0)
      }
    }

    "findByApiId throws for missing sprint" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val sprints = ExposedSprintRepository(database)

        shouldThrow<ResourceNotFoundException> {
          sprints.update(
            UpdateSprintCommand(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              sprintApiId = "spr_missing",
              name = "Nope",
              actorUserId = stack.actorId,
            )
          )
        }
      }
    }
  })
