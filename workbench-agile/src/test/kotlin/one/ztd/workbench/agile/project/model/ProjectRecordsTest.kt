package one.ztd.workbench.agile.project.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

class ProjectRecordsTest :
  StringSpec({
    "create project command stores identifier and lead" {
      val tenantId = UUID.randomUUID()
      val actorId = UUID.randomUUID()
      val command =
        CreateProjectCommand(
          tenantId = tenantId,
          identifier = "CORE",
          name = "Core Platform",
          description = "Main",
          createdBy = actorId,
          leadUserId = actorId,
        )

      command.identifier shouldBe "CORE"
      command.leadUserId shouldBe actorId
    }

    "update project command allows partial updates" {
      val command =
        UpdateProjectCommand(
          tenantId = UUID.randomUUID(),
          projectId = UUID.randomUUID(),
          name = "Renamed",
          identifier = null,
          description = null,
          nonMemberVisibility = NonMemberVisibility.READ_ONLY,
          nonMemberJoinPolicy = null,
          updatedBy = UUID.randomUUID(),
        )

      command.nonMemberVisibility shouldBe NonMemberVisibility.READ_ONLY
    }
  })
