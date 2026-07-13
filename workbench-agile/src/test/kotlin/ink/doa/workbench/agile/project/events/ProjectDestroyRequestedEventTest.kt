package ink.doa.workbench.agile.project.events

import ink.doa.workbench.agile.project.model.ProjectRecord
import ink.doa.workbench.agile.project.model.ProjectStatus
import ink.doa.workbench.kernel.common.ids.PublicId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.util.UUID

class ProjectDestroyRequestedEventTest :
  StringSpec({
    "from maps project and actor public ids" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val actorId = UUID.randomUUID()
      val requestedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z")
      val project =
        ProjectRecord(
          id = projectId,
          apiId = PublicId("prj_01JABCDEFGHJKMNPQRSTVWXYZ0"),
          tenantId = tenantId,
          identifier = "CORE",
          name = "Core",
          description = null,
          status = ProjectStatus.DESTROYING,
          leadUserId = actorId,
          createdBy = actorId,
        )

      ProjectDestroyRequestedEvent.from(
        project = project,
        tenantPublicId = PublicId("ten_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        deleteReason = "cleanup",
        requestedAt = requestedAt,
        requestedByPublicId = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ1"),
      ) shouldBe
        ProjectDestroyRequestedEvent(
          tenantId = "ten_01JABCDEFGHJKMNPQRSTVWXYZ0",
          projectId = "prj_01JABCDEFGHJKMNPQRSTVWXYZ0",
          requestedBy = "usr_01JABCDEFGHJKMNPQRSTVWXYZ1",
          deleteReason = "cleanup",
          requestedAt = requestedAt.toString(),
        )
    }
  })
