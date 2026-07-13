package ink.doa.workbench.agile.project

import ink.doa.workbench.agile.project.events.ProjectDestroyRequestedEvent
import ink.doa.workbench.kernel.common.ids.PublicId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

class ProjectDestroyRequestTest :
  StringSpec({
    "project destroy request carries outbox payload" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val request =
        ProjectDestroyRequest(
          tenantId = tenantId,
          projectId = projectId,
          deletedBy = UUID.randomUUID(),
          deleteReason = "cleanup",
          projectApiId = "prj_01",
          tenantApiId = "ten_01",
          payload =
            ProjectDestroyRequestedEvent(
              tenantId = "ten_01",
              projectId = "prj_01",
              requestedBy = PublicId.new("usr").value,
              deleteReason = "cleanup",
              requestedAt = "2026-07-10T12:00:00Z",
            ),
        )

      request.projectApiId shouldBe "prj_01"
      request.payload.projectId shouldBe "prj_01"
    }
  })
