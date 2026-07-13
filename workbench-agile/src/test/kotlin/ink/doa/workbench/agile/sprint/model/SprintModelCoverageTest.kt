package ink.doa.workbench.agile.sprint.model

import ink.doa.workbench.kernel.common.ids.PublicId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class SprintModelCoverageTest :
  StringSpec({
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()
    val actorId = UUID.randomUUID()

    "sprint status round-trips db values" {
      SprintStatus.fromDbValue("planned") shouldBe SprintStatus.PLANNED
      SprintStatus.fromDbValue("ACTIVE") shouldBe SprintStatus.ACTIVE
      SprintStatus.fromDbValue("closed") shouldBe SprintStatus.CLOSED
      SprintStatus.PLANNED.dbValue shouldBe "planned"
    }

    "sprint status rejects unknown db values" {
      shouldThrow<NoSuchElementException> { SprintStatus.fromDbValue("archived") }
    }

    "sprint record stores lifecycle metadata" {
      val record =
        SprintRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("spr"),
          tenantId = tenantId,
          projectId = projectId,
          name = "Sprint 1",
          goal = "Ship sprint APIs",
          status = SprintStatus.ACTIVE,
          startAt = now,
          endAt = now.plusWeeks(2),
          closedAt = null,
          createdBy = actorId,
          archivedAt = null,
          archivedBy = null,
          deletedAt = null,
          createdAt = now,
          updatedAt = now,
        )

      record.name shouldBe "Sprint 1"
      record.status shouldBe SprintStatus.ACTIVE
    }

    "sprint commands store actor and scope" {
      CreateSprintCommand(
          tenantId = tenantId,
          projectId = projectId,
          name = "Sprint 2",
          goal = null,
          startAt = now,
          endAt = now.plusWeeks(2),
          createdBy = actorId,
        )
        .name shouldBe "Sprint 2"

      UpdateSprintCommand(
          tenantId = tenantId,
          projectId = projectId,
          sprintApiId = "spr_abc",
          name = "Renamed",
          actorUserId = actorId,
        )
        .name shouldBe "Renamed"

      StartSprintCommand(
          tenantId = tenantId,
          projectId = projectId,
          sprintApiId = "spr_abc",
          actorUserId = actorId,
        )
        .sprintApiId shouldBe "spr_abc"

      CloseSprintCommand(
          tenantId = tenantId,
          projectId = projectId,
          sprintApiId = "spr_abc",
          actorUserId = actorId,
        )
        .actorUserId shouldBe actorId

      ArchiveSprintCommand(
          tenantId = tenantId,
          projectId = projectId,
          sprintApiId = "spr_abc",
          actorUserId = actorId,
        )
        .projectId shouldBe projectId

      DeleteSprintCommand(
          tenantId = tenantId,
          projectId = projectId,
          sprintApiId = "spr_abc",
          actorUserId = actorId,
          deleteReason = "duplicate",
        )
        .deleteReason shouldBe "duplicate"
    }
  })
