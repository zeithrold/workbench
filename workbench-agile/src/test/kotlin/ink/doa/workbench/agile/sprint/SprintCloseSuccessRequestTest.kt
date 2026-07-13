package ink.doa.workbench.agile.sprint

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class SprintCloseSuccessRequestTest :
  StringSpec({
    "holds sprint close success metadata" {
      val operationId = UUID.randomUUID()
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val actorId = UUID.randomUUID()
      val closedAt = OffsetDateTime.now(ZoneOffset.UTC)

      SprintCloseSuccessRequest(
          operationId = operationId,
          tenantId = tenantId,
          projectId = projectId,
          sprintApiId = "spr_1",
          operationApiId = "sop_1",
          closedAt = closedAt,
          actorUserId = actorId,
        )
        .operationApiId shouldBe "sop_1"
    }
  })
