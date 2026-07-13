package ink.doa.workbench.agile.sprint

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class SprintCloseFailureRequestTest :
  StringSpec({
    "holds sprint close failure metadata" {
      val operationId = UUID.randomUUID()
      val completedAt = OffsetDateTime.now(ZoneOffset.UTC)

      SprintCloseFailureRequest(
          operationId = operationId,
          tenantId = UUID.randomUUID(),
          projectId = UUID.randomUUID(),
          sprintApiId = "spr_1",
          operationApiId = "sop_1",
          error = "batch failed",
          completedAt = completedAt,
        )
        .error shouldBe "batch failed"
    }
  })
