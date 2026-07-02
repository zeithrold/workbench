package doa.ink.workbench.web.project

import doa.ink.workbench.core.common.ids.PublicId
import doa.ink.workbench.core.project.model.ProjectRecord
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

      response.id shouldBe record.apiId.value
      response.identifier shouldBe "CORE"
    }
  })
