package one.ztd.workbench.agile.project.events

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.util.UUID
import one.ztd.workbench.agile.project.model.ProjectRecord
import one.ztd.workbench.agile.project.model.ProjectStatus
import one.ztd.workbench.kernel.common.ids.PublicId

class ProjectDestroyedEventTest :
  StringSpec({
    "from maps deleted project metadata" {
      val tenantId = UUID.randomUUID()
      val deletedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z")
      val project =
        ProjectRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("prj"),
          tenantId = tenantId,
          identifier = "CORE",
          name = "Core",
          description = null,
          status = ProjectStatus.DESTROYING,
          leadUserId = UUID.randomUUID(),
          createdBy = UUID.randomUUID(),
          deletedAt = deletedAt,
        )

      ProjectDestroyedEvent.from(
          project = project,
          tenantPublicId = "ten_01JABCDEFGHJKMNPQRSTVWXYZ0",
          deletedAt = deletedAt,
          deleteReason = "cleanup",
        )
        .projectId shouldBe project.apiId.value
    }
  })
