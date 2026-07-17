package one.ztd.workbench.web.project

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import one.ztd.workbench.application.project.ProjectView

class ProjectResponseTest :
  StringSpec({
    "project response maps view fields" {
      val view =
        ProjectView(
          id = "prj_01EXAMPLE",
          identifier = "CORE",
          name = "Core Platform",
          description = "Platform work",
          status = "active",
          nonMemberVisibility = "invisible",
          nonMemberJoinPolicy = "admin_only",
          lead = null,
          archivedAt = null,
        )

      val response = ProjectResponse.from(view)

      response.id shouldBe "prj_01EXAMPLE"
      response.identifier shouldBe "CORE"
      response.status shouldBe "active"
    }
  })
