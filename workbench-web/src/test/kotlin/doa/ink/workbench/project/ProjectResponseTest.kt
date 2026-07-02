package doa.ink.workbench.project

import doa.ink.workbench.project.model.ProjectRecord
import doa.ink.workbench.shared.ids.PublicId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

class ProjectResponseTest :
  StringSpec({
    "project response uses public API id" {
      val record =
        ProjectRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("prj"),
          tenantId = UUID.randomUUID(),
          identifier = "CORE",
          name = "Core Platform",
          description = "Platform work",
        )

      val response = ProjectResponse.from(record)

      response.apiId shouldBe record.apiId.value
      response.identifier shouldBe "CORE"
    }
  })
