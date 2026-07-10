package ink.doa.workbench.core.sprint

import ink.doa.workbench.core.sprint.events.SprintCloseRequestedEvent
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

class SprintCloseRetryRequestTest :
  StringSpec({
    "sprint close retry request carries event payload" {
      val request =
        SprintCloseRetryRequest(
          tenantId = UUID.randomUUID(),
          projectId = UUID.randomUUID(),
          sprintApiId = "spr_01",
          operationApiId = "sop_01",
          payload =
            SprintCloseRequestedEvent(
              tenantId = "ten_01",
              projectId = "prj_01",
              sprintId = "spr_01",
              operationId = "sop_01",
              requestedBy = "usr_01",
            ),
          metadataTenantId = "ten_01",
        )

      request.sprintApiId shouldBe "spr_01"
      request.payload.operationId shouldBe "sop_01"
    }
  })
